#version 150

in vec2 texCoord;

out vec4 fragColor;

uniform sampler2D SceneSampler;

layout(std140) uniform SaturationData {
    vec4 params; // x = saturation, y = vibrance, z = contrast, w = unused
};

void main() {
    vec3 color = texture(SceneSampler, texCoord).rgb;

    float saturation = params.x;
    float vibrance = params.y;
    float contrast = params.z;

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));

    if (abs(vibrance) > 0.001) {
        float maxC = max(color.r, max(color.g, color.b));
        float minC = min(color.r, min(color.g, color.b));
        float chroma = maxC - minC;
        color = mix(vec3(luma), color, 1.0 + vibrance * (1.0 - chroma));
        luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    }

    color = mix(vec3(luma), color, saturation);

    if (abs(contrast - 1.0) > 0.001) {
        color = (color - 0.5) * contrast + 0.5;
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
