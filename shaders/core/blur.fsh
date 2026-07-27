#version 150

in vec2 fragCoord;
in vec2 pixelCoord;
in vec2 texCoord;
in vec2 rectSize;
in vec4 cornerRadii;
in float guiScale;
in float blurRadius;
in vec2 texelSize;
in vec4 tintColor;
in vec2 resolution;

out vec4 fragColor;

uniform sampler2D Sampler0;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.yz : r.xw;
    r.x = (p.y > 0.0) ? r.y : r.x;

    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

// Wide, smooth single-pass gaussian. Unlike the old version the sample step is
// scaled by the radius (so the kernel actually spans the requested blur), and the
// whole kernel is rotated by a per-pixel angle which turns grid banding into fine
// noise — much cleaner for big radii.
vec4 gaussianBlur(vec2 uv, float radius) {
    const int S = 6;                              // 13x13 taps
    float step = max(radius, 0.5) / float(S);     // texel step covering the radius
    float sigma = max(radius * 0.5, 0.5);
    float twoSigma2 = 2.0 * sigma * sigma;

    float ang = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) * 6.2831853;
    float ca = cos(ang);
    float sa = sin(ang);
    mat2 rot = mat2(ca, -sa, sa, ca);

    vec4 col = vec4(0.0);
    float total = 0.0;

    for (int x = -S; x <= S; x++) {
        for (int y = -S; y <= S; y++) {
            float d2 = float(x * x + y * y) * step * step;
            float w = exp(-d2 / twoSigma2);

            vec2 offset = rot * (vec2(float(x), float(y)) * step);
            vec2 sampleUV = clamp(uv + offset * texelSize, vec2(0.001), vec2(0.999));

            col += texture(Sampler0, sampleUV) * w;
            total += w;
        }
    }

    return col / total;
}

void main() {
    vec2 halfSize = rectSize * 0.5;
    vec2 center = pixelCoord - halfSize;

    float maxRadius = min(halfSize.x, halfSize.y);
    vec4 rRadii = min(cornerRadii, vec4(maxRadius));

    float dist = roundedBoxSDF(center, halfSize, rRadii);

    // Isotropic SDF AA from the gradient length of the distance field (matches
    // rect.fsh). The expanded quad from blur.vsh gives the band room on the
    // straight edges too, so corners and straight sides stay aligned.
    float aa = max(length(vec2(dFdx(dist), dFdy(dist))), 0.0001);
    float alpha = 1.0 - smoothstep(-aa * 0.75, aa * 0.75, dist);

    if (alpha < 0.01) {
        discard;
    }

    vec4 blurred = gaussianBlur(texCoord, blurRadius);

    vec3 finalColor = mix(blurred.rgb, tintColor.rgb, tintColor.a);

    fragColor = vec4(finalColor, alpha);
}
