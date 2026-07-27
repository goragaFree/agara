#version 150

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

    // Expand the quad outward by a small margin so the SDF anti-aliasing band has
    // room on the straight edges too (without this the straight sides hard-clip
    // their outer AA at the quad edge while the rounded corners keep theirs -> the
    // corner/edge junction reads as a 1px step). Matches rect.vsh / glass_rect.vsh.
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

    vec2 fbPos = screenPos * guiScale;
    texCoord = vec2(fbPos.x / resolution.x, 1.0 - (fbPos.y / resolution.y));
}