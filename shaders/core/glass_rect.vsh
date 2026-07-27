#version 150

// Dedicated vertex shader for the frosted-glass rounded rect. Same BlurData UBO
// and varyings as blur.vsh, but it EXPANDS the quad outward by a small margin so
// the SDF anti-aliasing band has room on the straight edges too (blur.vsh draws
// the quad at the exact rect, which hard-clips the outer AA on the straight sides
// while the rounded corners keep theirs -> the corner/edge junction reads as a
// 1px step). Matches rect.vsh so glass fill, outline and plain rects all line up.
layout(std140) uniform BlurData {
    vec4 rect;
    vec4 screen;
    vec4 framebufferSize;
    vec4 radii;
    vec4 color;
};

out vec2 fragCoord;
out vec2 pixelCoord;
out vec2 texCoord;
out vec2 rectSize;
out vec4 cornerRadii;
out float guiScale;
out float blurRadius;
out vec2 texelSize;
out vec4 tintColor;
out vec2 resolution;
out float distortStrength;   // liquid-glass edge refraction (framebufferSize.z)
out float fresnelStrength;   // liquid-glass edge highlight   (framebufferSize.w)

void main() {
    vec2 positions[6] = vec2[](
    vec2(0.0, 0.0),
    vec2(1.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 1.0)
    );

    vec2 pos = positions[gl_VertexID];

    // expand the quad so the SDF AA falloff has room on every side (see header)
    float margin = 1.5;
    vec2 expand = vec2(margin) / max(rect.zw, vec2(1.0));
    vec2 epos = pos * (1.0 + 2.0 * expand) - expand;

    vec2 screenPos = rect.xy + epos * rect.zw;
    vec2 ndcPos = (screenPos / screen.xy) * 2.0 - 1.0;
    ndcPos.y = -ndcPos.y;

    gl_Position = vec4(ndcPos, 0.0, 1.0);

    fragCoord = epos;
    pixelCoord = epos * rect.zw;
    rectSize = rect.zw;
    cornerRadii = radii;
    guiScale = screen.z;
    blurRadius = screen.w;
    resolution = framebufferSize.xy;
    texelSize = 1.0 / resolution;
    tintColor = color;
    distortStrength = framebufferSize.z;
    fresnelStrength = framebufferSize.w;

    vec2 fbPos = screenPos * guiScale;
    texCoord = vec2(fbPos.x / resolution.x, 1.0 - (fbPos.y / resolution.y));
}
