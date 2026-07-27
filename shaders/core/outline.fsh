#version 150

in vec2 fragCoord;
in vec2 pixelCoord;
in vec2 rectSize;
in vec4 cornerRadii;
in vec4 fragColors[8];
in float fragThicknesses[8];
in float fragSmoothness;
in float guiScale;
in float maxThickness;

out vec4 fragColor;

const float PI = 3.14159265359;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.yz : r.xw;
    r.x = (p.y > 0.0) ? r.y : r.x;

    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

float getPerimeterPosition(vec2 center, vec2 halfSize) {
    vec2 normCenter = center / max(halfSize, vec2(0.001));
    float angle = atan(normCenter.y, normCenter.x);
    float t = (angle + PI) / (2.0 * PI);
    return fract(t + 0.125);
}

vec4 getPerimeterColor(float t) {
    float segment = t * 8.0;
    int idx = int(floor(segment));
    float frac = fract(segment);

    int idx1 = idx % 8;
    int idx2 = (idx + 1) % 8;

    return mix(fragColors[idx1], fragColors[idx2], smoothstep(0.0, 1.0, frac));
}

float getPerimeterThickness(float t) {
    float segment = t * 8.0;
    int idx = int(floor(segment));
    float frac = fract(segment);

    int idx1 = idx % 8;
    int idx2 = (idx + 1) % 8;

    return mix(fragThicknesses[idx1], fragThicknesses[idx2], smoothstep(0.0, 1.0, frac));
}

void main() {
    vec2 halfSize = rectSize * 0.5;
    vec2 center = pixelCoord - halfSize;

    float maxRadius = min(halfSize.x, halfSize.y);
    vec4 radii = min(cornerRadii, vec4(maxRadius));

    float dist = roundedBoxSDF(center, halfSize, radii);

    float perimeterPos = getPerimeterPosition(center, halfSize);
    vec4 outlineColor = getPerimeterColor(perimeterPos);
    float thickness = getPerimeterThickness(perimeterPos);

    // Isotropic SDF AA from the gradient length of the distance field: constant
    // ~1px around the whole perimeter (including corners), so the outline edge
    // doesn't read stepped on the curve. See rect.fsh for the rationale.
    float aa = max(length(vec2(dFdx(dist), dFdy(dist))), 0.0001) * max(fragSmoothness, 1.0);

    // Trace the outline's outer boundary with the EXACT same edge ramp the fill
    // uses (rect.fsh: smoothstep over +/-0.75aa), so the outline hugs the same
    // rounded corner as the fill. The ring is the fill coverage minus the same
    // coverage shrunk inward by `thickness`.
    float coverOuter = 1.0 - smoothstep(-aa * 0.75, aa * 0.75, dist);
    float coverInner = 1.0 - smoothstep(-aa * 0.75, aa * 0.75, dist + thickness);
    float outlineMask = clamp(coverOuter - coverInner, 0.0, 1.0);

    if (outlineMask < 0.01) {
        discard;
    }

    fragColor = vec4(outlineColor.rgb, outlineColor.a * outlineMask);
}