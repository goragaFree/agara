#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord;
flat in float distanceRange;

out vec4 fragColor;

// Среднее из трёх каналов.
//
// В атласе лежит не картинка буквы, а расстояние до её края — по одному полю на канал.
// Три поля вместо одного нужны ровно ради острых углов: на изломе контура каналы
// расходятся, и середина из трёх восстанавливает угол, который одно поле бы скруглило.
float median(vec3 value) {
    return max(min(value.r, value.g), min(max(value.r, value.g), value.b));
}

void main() {
    vec3 field = texture(Sampler0, texCoord).rgb;

    // Половина — это край буквы. Отрицательное снаружи, положительное внутри.
    float distance = median(field) - 0.5;

    // Перевод расстояния из долей атласа в пиксели экрана.
    //
    // Считается через производные координаты текстуры, поэтому масштаб текста, масштаб
    // интерфейса и любой поворот учитываются сами собой: чем крупнее буква на экране,
    // тем на большее число пикселей растягивается полоса расстояний.
    vec2 unitRange = vec2(distanceRange) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(texCoord);
    float pixelRange = max(0.5 * dot(unitRange, screenTexSize), 1.0);

    float coverage = clamp(distance * pixelRange + 0.5, 0.0, 1.0);

    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;

    if (color.a <= 0.0) {
        discard;
    }

    fragColor = color;
}
