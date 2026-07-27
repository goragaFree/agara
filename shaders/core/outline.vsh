#version 150

// Batched outline data. Layout per outline = 13 vec4 (208 bytes):
// [0]  rect   (x, y, w, h)
// [1]  radii  (tl, tr, br, bl)
// [2]  params (smoothness, _, _, _)
// [3..10] colors[8]
// [11] thicknesses.xyzw
// [12] thicknesses2.xyzw
// Header = screen (vec4) + meta (ivec4) = 32 bytes.
// 72 * 208 + 32 = 15008, under 16384 UBO minimum.
// data[] size (936) must equal MAX_OUTLINES(72) * 13.

layout(std140) uniform OutlineData {
    vec4 screen;   // x=screenW, y=screenH, z=guiScale, w=unused
    ivec4 meta;    // x=outlineCount
    vec4 data[936];
};

out vec2 fragCoord;
out vec2 pixelCoord;
out vec2 rectSize;
out vec4 cornerRadii;
out vec4 fragColors[8];
out float fragThicknesses[8];
out float fragSmoothness;
out float guiScale;
out float maxThickness;

void main() {
    int outlineIndex = gl_VertexID / 6;
    int vertexIndex  = gl_VertexID % 6;
    int base = outlineIndex * 13;

    vec4 rect   = data[base];
    vec4 radii  = data[base + 1];
    vec4 params = data[base + 2];

    float soft = params.x;

    vec4 t1 = data[base + 11];
    vec4 t2 = data[base + 12];

    float maxT = max(
        max(max(t1.x, t1.y), max(t1.z, t1.w)),
        max(max(t2.x, t2.y), max(t2.z, t2.w))
    );

    vec2 positions[6] = vec2[](
        vec2(0.0, 0.0),
        vec2(1.0, 0.0),
        vec2(1.0, 1.0),
        vec2(0.0, 0.0),
        vec2(1.0, 1.0),
        vec2(0.0, 1.0)
    );

    vec2 pos = positions[vertexIndex];

    float padding = maxT + 2.0;
    vec2 expandedPos = rect.xy - padding + pos * (rect.zw + padding * 2.0);

    vec2 ndcPos = (expandedPos / screen.xy) * 2.0 - 1.0;
    ndcPos.y = -ndcPos.y;

    gl_Position = vec4(ndcPos, 0.0, 1.0);

    fragCoord  = pos;
    pixelCoord = pos * (rect.zw + padding * 2.0) - padding;
    rectSize   = rect.zw;
    cornerRadii = radii;

    fragSmoothness = soft;
    guiScale       = screen.z;
    maxThickness   = maxT;

    for (int i = 0; i < 8; i++) {
        fragColors[i] = data[base + 3 + i];
    }

    fragThicknesses[0] = t1.x;
    fragThicknesses[1] = t1.y;
    fragThicknesses[2] = t1.z;
    fragThicknesses[3] = t1.w;
    fragThicknesses[4] = t2.x;
    fragThicknesses[5] = t2.y;
    fragThicknesses[6] = t2.z;
    fragThicknesses[7] = t2.w;
}