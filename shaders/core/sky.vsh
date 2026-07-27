#version 150

// Fullscreen triangle generated purely from gl_VertexID.
// Drawn with VertexFormats.EMPTY (a dummy vertex buffer is bound).
void main() {
    vec2 verts[3] = vec2[](
        vec2(-1.0, -1.0),
        vec2( 3.0, -1.0),
        vec2(-1.0,  3.0)
    );
    gl_Position = vec4(verts[gl_VertexID], 0.0, 1.0);
}
