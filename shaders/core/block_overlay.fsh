#version 150

// Block-overlay shader, mapped onto the targeted block's faces in 3D.
// Layered, bottom to top:
//   1. Glass  - a transparent frosted-glass blur of the world behind the block
//               (no fill colour); the block reads as cold matte glass.
//   2. Shader - the particle effect, drawn on top of the glass (NOT blurred).
//   3. Outline- a coloured border along every face edge (the block wireframe).
//
//   params.x = time            params.y = particle alpha
//   params.z = particle count  params.w = particles on (1/0)
//   outline.rgb/.w = outline colour / thickness
//   glassParams.x = blur radius (0 = transparent, no glass)
//   glassParams.y = particle brightness (1 = original)
//   res.xy = framebuffer size

layout(std140) uniform BlockData {
    mat4 mvp;
    vec4 boxMin;
    vec4 boxMax;
    vec4 params;
    vec4 outline;
    vec4 glassParams;
    vec4 res;
};

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D Sampler0; // copy of the scene framebuffer (for the blur)

float hash(float n) { return fract(sin(n) * 43758.5453123); }

vec2 getPos(float id, float t) {
    float h1 = hash(id);
    float h2 = hash(id + 50.0);
    float speed = 0.05 + h1 * 0.15;
    vec2 dir = vec2(sin(h1 * 6.28), cos(h2 * 6.28));
    vec2 pos = dir * t * speed + (vec2(h1, h2) * 2.0 - 1.0);
    return fract(pos * 0.5 + 0.5) * 2.4 - 1.2;
}

vec3 particles(vec2 uv, float count, float t) {
    vec3 finalCol = vec3(0.0);
    for (float i = 0.0; i < count; i++) {
        float h = hash(i);
        vec2 p = getPos(i, t);
        float d = length(uv - p);
        float sharp = 0.0006 / (d + 0.0001);
        float strongGlow = 0.008 / (d * d + 0.0004);
        float wideBloom = 0.02 / (d + 0.08);
        vec3 col = mix(vec3(0.0, 0.5, 1.0), vec3(0.4, 0.9, 1.0), h);
        float pulse = 0.7 + 0.3 * sin(t * 3.0 + i);
        finalCol += col * sharp * pulse;
        finalCol += col * strongGlow * 0.5 * pulse;
        finalCol += col * wideBloom * 0.2;
    }
    finalCol = pow(finalCol, vec3(0.8));
    return finalCol;
}

// Gaussian blur of the scene copy around the given screen UV. The fixed number
// of taps is spread evenly across the whole radius so wide blurs stay smooth.
vec3 blurScene(vec2 uv, float radius) {
    vec2 texel = vec2(1.0 / res.x, 1.0 / res.y);
    const int samples = 6; // taps per side -> (2*6+1)^2 = 169 taps
    float reach = radius * 4.0; // blur reach in pixels
    float stepPx = reach / float(samples);
    float sigma = max(float(samples) * 0.5, 0.1);
    float twoSigma2 = 2.0 * sigma * sigma;

    vec3 sum = vec3(0.0);
    float total = 0.0;
    for (int x = -samples; x <= samples; x++) {
        for (int y = -samples; y <= samples; y++) {
            float dd = float(x * x + y * y);
            float w = exp(-dd / twoSigma2);
            vec2 o = vec2(float(x), float(y)) * texel * stepPx;
            vec2 s = clamp(uv + o, vec2(0.001), vec2(0.999));
            sum += texture(Sampler0, s).rgb * w;
            total += w;
        }
    }
    return sum / total;
}

void main() {
    float t = params.x * 0.8;
    float particleAlpha = params.y;
    float count = params.z;
    float particlesOn = params.w;

    float blur = glassParams.x;
    float particleBrightness = glassParams.y;

    vec3 col = vec3(0.0);
    float a = 0.0;

    // 1. frosted glass: a transparent block. Blur the scene at the face and
    // keep its real colours so the surroundings show through blurred, like
    // real glass. Only a faint cool tint / brightness lift keeps the "glass"
    // feel without washing out the colour. blur == 0 -> fully transparent.
    if (blur > 0.0) {
        vec2 suv = gl_FragCoord.xy / res.xy;
        vec3 frosted = blurScene(suv, blur);
        frosted = mix(frosted, frosted * vec3(0.95, 0.98, 1.05), 0.5); // faint cool tint
        frosted *= 1.06;                                                // slight lift
        vec3 particleTint = vec3(0.2, 0.7, 1.0);                        // particle base colour
        frosted = mix(frosted, frosted * particleTint, 0.12);           // a touch of particle colour
        col = frosted;
        a = 1.0;
    }

    // 2. particle shader, composited on top of the glass (not blurred)
    if (particlesOn > 0.5) {
        vec2 uv = vUv * 2.4 - 1.2;
        vec3 p = particles(uv, count, t) * particleBrightness;
        float pa = clamp(max(p.r, max(p.g, p.b)), 0.0, 1.0) * particleAlpha;
        col = mix(col, p, pa);
        a = max(a, pa);
    }

    // 3. outline along the face edges (block wireframe)
    float thickness = outline.w;
    if (thickness > 0.0) {
        float distEdge = min(min(vUv.x, 1.0 - vUv.x), min(vUv.y, 1.0 - vUv.y));
        float edge = 1.0 - smoothstep(thickness * 0.6, thickness, distEdge);
        if (edge > 0.0) {
            col = mix(col, outline.rgb, edge);
            a = max(a, edge);
        }
    }

    fragColor = vec4(col, a);
}
