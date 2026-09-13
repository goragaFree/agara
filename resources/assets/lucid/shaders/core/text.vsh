#version 330

// Копия объявлений из ванильного gui.vsh: импортировать их нельзя,
// шейдер собирается на старте, когда ресурспаков ещё нет.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;

out vec4 vertexColor;
out vec2 texCoord;
flat out float distanceRange;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;

    // Место буквы в атласе, уже в долях от нуля до единицы.
    texCoord = UV0;

    // Ширина полосы расстояний в пикселях атласа. Приходит в шестнадцатых долях,
    // как и всё целочисленное в остальных шейдерах клиента.
    distanceRange = float(UV1.x) / 16.0;
}
