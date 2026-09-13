#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 localPos;
flat in vec2 halfSize;
flat in float cornerRadius;
flat in float ringThickness;

out vec4 fragColor;

// Четыре точки внутри пикселя, повёрнутая сетка.
//
// Ровная сетка 2×2 бесполезна именно там, где нужна: обе её точки в строке пересекают
// почти горизонтальный край одновременно и дают те же две градации вместо четырёх.
// Поворот на арктангенс одной второй разводит точки по всем четырём уровням при любом
// наклоне края, кроме ровно того же арктангенса.
const vec2 SUBPIXEL[4] = vec2[4](
    vec2( 0.125,  0.375),
    vec2( 0.375, -0.125),
    vec2(-0.125, -0.375),
    vec2(-0.375,  0.125)
);

// Знаковое расстояние до края фигуры: отрицательное внутри, ноль на границе.
// SHAPE задаётся при сборке пайплайна: 2 — обычное скругление, 5 — squircle
// с непрерывным углом в духе Apple.
float shapeDistance(vec2 point, vec2 half_, float radius) {
    vec2 offset = abs(point) - (half_ - vec2(radius));
    vec2 outer = max(offset, vec2(0.0));
    float inner = min(max(offset.x, offset.y), 0.0);

#if SHAPE == 2
    float outerLength = length(outer);
#else
    float n = float(SHAPE);
    float outerLength = pow(pow(outer.x, n) + pow(outer.y, n), 1.0 / n);
#endif

    return outerLength + inner - radius;
}

// Доля пикселя внутри фигуры с краем на нулевом уровне {@code value}.
float coverageOf(float value, float edge) {
    return clamp(0.5 - value / edge, 0.0, 1.0);
}

// Заполнение либо кольцо в одной точке.
float shapeCoverage(vec2 point, vec2 half_, float radius, float edge) {
    float distance = shapeDistance(point, half_, radius);
    float coverage = coverageOf(distance, edge);

    // Обводка — разность двух фигур: внешней и такой же, ужатой на толщину.
    // Оба края считаются по одному и тому же расстоянию, поэтому обводка тоньше
    // пикселя не рвётся, а честно теряет плотность.
    if (ringThickness > 0.0) {
        coverage -= coverageOf(distance + ringThickness, edge);
    }

    return clamp(coverage, 0.0, 1.0);
}

void main() {
    // Радиус больше половины стороны превратил бы фигуру в кашу.
    float radius = min(cornerRadius, min(halfSize.x, halfSize.y));

    // Размер пикселя экрана в единицах фигуры. Производные берутся до всякого
    // ветвления и только от координаты: localPos меняется линейно, поэтому конечная
    // разность по квадрату 2×2 даёт точный ответ, а не оценку, как было бы с расстоянием.
    vec2 pixelX = dFdx(localPos);
    vec2 pixelY = dFdy(localPos);
    float pixelSize = max(0.5 * (length(pixelX) + length(pixelY)), 0.0001);

    // Сглаживание каждой отдельной точки — половина пикселя.
    //
    // Широко размывать больше не нужно: градации теперь даёт сама сетка из четырёх
    // точек, а не растяжка края. Именно растяжка и выедала обводку на косых участках.
    float edge = 0.5 * pixelSize;

    // Обводка тоньше полосы сглаживания растворилась бы в тумане.
    if (ringThickness > 0.0) {
        edge = min(edge, max(0.25 * pixelSize, 0.5 * ringThickness));
    }

    float coverage = 0.0;

    for (int i = 0; i < 4; i++) {
        vec2 point = localPos + SUBPIXEL[i].x * pixelX + SUBPIXEL[i].y * pixelY;
        coverage += shapeCoverage(point, halfSize, radius, edge);
    }

    coverage *= 0.25;

    vec4 color = vertexColor * ColorModulator;
    color.a *= clamp(coverage, 0.0, 1.0);

    if (color.a <= 0.0) {
        discard;
    }

    fragColor = color;
}
