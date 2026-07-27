#version 150

// Samples a pre-blurred (Kawase) full-screen texture inside a rounded rect and
// tints it — a frosted-glass panel. Paired with glass_rect.vsh (same BlurData
// layout), which expands the quad so the AA band below has room on every side.
// screen.w (blurRadius slot) carries the panel opacity used for fade-in animations.

in vec2 fragCoord;
in vec2 pixelCoord;
in vec2 texCoord;
in vec2 rectSize;
in vec4 cornerRadii;
in float guiScale;
in float blurRadius;   // reused as panel opacity
in vec2 texelSize;
in vec4 tintColor;     // rgb = tint, a = tint strength (mix amount)
in vec2 resolution;
in float distortStrength;   // liquid-glass edge refraction (UV)
in float fresnelStrength;   // liquid-glass edge highlight (0..1)

out vec4 fragColor;

uniform sampler2D Sampler0;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.yz : r.xw;
    r.x = (p.y > 0.0) ? r.y : r.x;

    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

void main() {
    vec2 halfSize = rectSize * 0.5;
    vec2 center = pixelCoord - halfSize;

    float maxRadius = min(halfSize.x, halfSize.y);
    vec4 rRadii = min(cornerRadii, vec4(maxRadius));

    float dist = roundedBoxSDF(center, halfSize, rRadii);

    // Isotropic SDF AA from the gradient length of the distance field (matches
    // rect.fsh). The expanded quad from glass_rect.vsh gives the band room on the
    // straight edges too, so corners and straight sides stay aligned.
    float aa = max(length(vec2(dFdx(dist), dFdy(dist))), 0.0001);
    float alpha = 1.0 - smoothstep(-aa * 0.75, aa * 0.75, dist);

    float opacity = clamp(blurRadius, 0.0, 1.0);
    alpha *= opacity;

    if (alpha < 0.01) {
        discard;
    }

    // ---- liquid-glass edge refraction + rim highlight (Rockstar-style) ----
    // fresnel: 0 in the middle, rising to 1 at the panel edge.
    float maxR = min(halfSize.x, halfSize.y);
    float edge = 1.0 - clamp(abs(dist) / max(maxR, 0.0001), 0.0, 1.0);
    float fresnel = edge * edge * edge;             // pow(edge, 3)

    // push the sampled UV outward along the radial direction near the edges
    vec2 dir = (length(center) > 0.0001) ? normalize(center) : vec2(0.0);
    vec2 uv = texCoord + dir * fresnel * distortStrength;

    vec3 scene = texture(Sampler0, uv).rgb;
    vec3 finalColor = mix(scene, tintColor.rgb, tintColor.a);
    finalColor += vec3(fresnel * fresnelStrength * 0.6);   // soft rim light

    fragColor = vec4(finalColor, alpha);
}
