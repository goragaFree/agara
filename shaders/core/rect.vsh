#version 150

// Batched rect data. Layout per rect = 12 vec4 (192 bytes):
//   [0] rect (x, y, w, h)
//   [1] radii (tl, tr, br, bl)
//   [2] params (innerBlur, _, _, _)
//   [3..11] colors[9] (9-patch gradient)
// Header = screen (vec4) + meta (ivec4) = 32 bytes.
// 85 rects * 192 + 32 = 16352 bytes, under the 16384 UBO guaranteed minimum.
// data[] size (1020) must equal MAX_RECTS(85) * 12 and match RectPipeline.java.
layout(std140) uniform RectData {
    vec4 screen;     // x=screenW, y=screenH, z=guiScale, w=unused
    ivec4 meta;      // x=rectCount
    vec4 data[1020];
};

out vec2 fragCoord;
out vec2 pixelCoord;
out vec2 rectSize;
out vec4 cornerRadii;
out vec4 fragColors[9];
out float guiScale;
out float innerBlur;
out float softness;
out float cutInner;

void main() {
    int rectIndex = gl_VertexID / 6;
    int vertexIndex = gl_VertexID % 6;
    int base = rectIndex * 12;

    vec4 rect = data[base];
    vec4 radii = data[base + 1];
    vec4 params = data[base + 2];

    vec2 positions[6] = vec2[](
    vec2(0.0, 0.0),
    vec2(1.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 1.0)
    );

    vec2 pos = positions[vertexIndex];

    // Expand the quad outward by a margin so the SDF anti-aliasing falloff has
    // room to fade out instead of being clipped at the quad edge (which would
    // hard-cut the outer edge of rounded corners and read as stair-steps). A
    // small baseline margin covers normal-rect AA; a soft shadow/glow uses the
    // larger `softness`. The SDF is still evaluated against the original rect
    // size, and inside the shape epos spans 0..1 exactly as pos did, so normal
    // and gradient rects are unchanged apart from the now-visible smooth edge.
    float soft = params.y;
    float margin = max(soft, 1.5);
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
    innerBlur = params.x;
    softness = soft;
    cutInner = params.z;

    for (int i = 0; i < 9; i++) {
        fragColors[i] = data[base + 3 + i];
    }
}
