#version 150

// Generates the 6 faces of an axis-aligned box (the targeted block's bounding
// box, in camera-relative world space) purely from gl_VertexID. Drawn with
// VertexFormats.EMPTY (a dummy vertex buffer is bound) and draw(0, 36).
//
// Each face is two triangles; every vertex carries a [0,1] face-local UV that
// the fragment shader uses to map the screen-space effect onto the face.

layout(std140) uniform BlockData {
    mat4 mvp;        // projection * modelView (camera-relative world -> clip)
    vec4 boxMin;     // xyz = min corner (camera-relative)
    vec4 boxMax;     // xyz = max corner (camera-relative)
    vec4 params;      // x = time, y = particle alpha, z = count, w = particles on
    vec4 outline;     // rgb = outline colour, w = edge thickness (0 = no outline)
    vec4 glassParams; // x = blur radius (0 = transparent, no glass), y = particle brightness
    vec4 res;         // x = framebuffer width, y = framebuffer height
};

out vec2 vUv;

void main() {
    vec3 mn = boxMin.xyz;
    vec3 mx = boxMax.xyz;

    int face = gl_VertexID / 6;
    int vert = gl_VertexID % 6;

    // two triangles covering the unit face: (0,0)(1,0)(1,1) + (0,0)(1,1)(0,1)
    vec2 quad[6] = vec2[](
        vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0),
        vec2(0.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0)
    );
    vec2 uv = quad[vert];

    vec3 pos;
    if (face == 0) {            // -X
        pos = vec3(mn.x, mix(mn.y, mx.y, uv.y), mix(mn.z, mx.z, uv.x));
    } else if (face == 1) {     // +X
        pos = vec3(mx.x, mix(mn.y, mx.y, uv.y), mix(mn.z, mx.z, uv.x));
    } else if (face == 2) {     // -Y
        pos = vec3(mix(mn.x, mx.x, uv.x), mn.y, mix(mn.z, mx.z, uv.y));
    } else if (face == 3) {     // +Y
        pos = vec3(mix(mn.x, mx.x, uv.x), mx.y, mix(mn.z, mx.z, uv.y));
    } else if (face == 4) {     // -Z
        pos = vec3(mix(mn.x, mx.x, uv.x), mix(mn.y, mx.y, uv.y), mn.z);
    } else {                    // +Z
        pos = vec3(mix(mn.x, mx.x, uv.x), mix(mn.y, mx.y, uv.y), mx.z);
    }

    vUv = uv;
    gl_Position = mvp * vec4(pos, 1.0);
}
