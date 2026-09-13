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

// Тень пользуется той же вершинной программой, что и фигуры, поэтому и приходящие
// атрибуты те же. Меняется только смысл последнего: вместо толщины обводки в нём
// едет радиус размытия.
#define blurRadius ringThickness

// Знаковое расстояние до края фигуры: отрицательное внутри, ноль на границе.
// SHAPE задаётся при сборке пайплайна: 2 — обычное скругление, 5 — squircle.
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

void main() {
    // Радиус больше половины стороны превратил бы фигуру в кашу.
    float radius = min(cornerRadius, min(halfSize.x, halfSize.y));

    float distance = shapeDistance(localPos, halfSize, radius);

    // Размер пикселя в единицах фигуры. Нужен на случай нулевого размытия: тогда
    // тень вырождается в обычную фигуру и край всё равно должен быть сглажен.
    vec2 pixelX = dFdx(localPos);
    vec2 pixelY = dFdy(localPos);
    float pixelSize = max(0.5 * (length(pixelX) + length(pixelY)), 0.0001);

    // Полоса затухания. Пиксель — нижняя граница: уже неё переход рвётся на
    // ступеньки, потому что растр просто не может показать градиент тоньше.
    float falloff = max(blurRadius, pixelSize);

    // Внутри фигуры тени нет.
    //
    // Тень — это то, что видно вокруг панели, а не под ней. Заливать её целиком и
    // надеяться, что панель всё закроет, нельзя: подложка полупрозрачная, и тень сквозь
    // неё просвечивает — середина панели становится темнее самой себя.
    //
    // Резать ровно по краю фигуры нельзя. Край панели сглажен и занимает не ровно
    // пиксель, а его часть. Если тень обрывается в той же точке без сглаживания,
    // вдоль дуги остаётся цепочка то перекрытых, то пропущенных точек — те самые
    // светлые и тёмные крапинки на углах.
    //
    // Поэтому тень гаснет плавно и заходит под панель на один пиксель. Этот пиксель
    // целиком перекрыт самой панелью, так что внутрь ничего не просвечивает.
    float mask = clamp((distance + pixelSize) / pixelSize, 0.0, 1.0);

    if (mask <= 0.0) {
        discard;
    }

    // Снаружи — плавное затухание от единицы у края до нуля на расстоянии размытия.
    //
    // Настоящее размытие по Гауссу здесь не нужно и стоило бы прохода по текстуре.
    // У скруглённой фигуры линии равного расстояния и есть линии равной яркости
    // размытой тени, так что картинка выходит та же.
    float t = clamp(1.0 - max(distance, 0.0) / falloff, 0.0, 1.0);

    // Квадрат вместо прямой: рядом с панелью тень гаснет быстро, а дальше тянет
    // длинный едва заметный хвост. Линейное затухание читается как грязная кайма.
    float coverage = t * t * mask;

    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;

    if (color.a <= 0.0) {
        discard;
    }

    fragColor = color;
}
