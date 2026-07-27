#version 150

in vec2 texCoord;
in vec2 texelSize;
in float offset;

out vec4 fragColor;

uniform sampler2D Sampler0;

// Canonical dual-Kawase UPSAMPLE: an 8-tap tent (4 axis + 4 diagonal, diagonals
// weighted 2x). This is what actually smooths the low-res mip levels back up — a
// plain 5-tap diamond (the downsample kernel) leaves visible blockiness ("pixels").
void main() {
    vec2 hp = texelSize * 0.5 * offset;

    vec4 sum = texture(Sampler0, texCoord + vec2(-hp.x * 2.0, 0.0));
    sum += texture(Sampler0, texCoord + vec2(-hp.x, hp.y)) * 2.0;
    sum += texture(Sampler0, texCoord + vec2(0.0, hp.y * 2.0));
    sum += texture(Sampler0, texCoord + vec2(hp.x, hp.y)) * 2.0;
    sum += texture(Sampler0, texCoord + vec2(hp.x * 2.0, 0.0));
    sum += texture(Sampler0, texCoord + vec2(hp.x, -hp.y)) * 2.0;
    sum += texture(Sampler0, texCoord + vec2(0.0, -hp.y * 2.0));
    sum += texture(Sampler0, texCoord + vec2(-hp.x, -hp.y)) * 2.0;

    fragColor = sum / 12.0;
}
