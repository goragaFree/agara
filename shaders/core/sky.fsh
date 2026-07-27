#version 150

// ---------------------------------------------------------------------------
//  "Lava Pool" sky shader
//  Original idea: Krzysztof Kondrak (@k_kondrak)
//
//  The original Shadertoy is a multi-pass buffer effect (feedback buffer
//  iChannel0 + noise texture iChannel1). Neither exists in Minecraft's single
//  sky pass, so the noise/swirl are transcribed procedurally and the warm
//  "lava" look is produced directly from a swirled marble field through a hot
//  palette.
//
//  The whole effect is evaluated on the 3D world-space view ray (reconstructed
//  from the inverse view/proj matrices). This keeps the sky fixed in the world
//  AND avoids any seam: the noise wraps seamlessly around the sky sphere instead
//  of using a 2D atan mapping that produced a visible vertical "wall".
// ---------------------------------------------------------------------------

layout(std140) uniform SkyData {
    vec4 iResolutionTime; // xy = resolution, z = time (s), w = mode (0 = lava, 1 = eyes, 2 = cosmo, 3 = cosmo2)
    mat4 invProj;
    mat4 invView;
    vec4 iParams;         // x = cosmo2 wobble, y = cosmo2 "black holes" count (1..2)
};

out vec4 fragColor;

#define PI 3.14159265359
#define TWO_PI 6.28318530718
#define EYE_EDGE 0.005

// shared per-frame globals (set in main, read by the cosmo helper functions)
vec2 iResolution;
float iTime;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

// 3D value noise in [-1, 1]
float noise(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = smoothstep(0.0, 1.0, f);
    float n = mix(
        mix(mix(hash(p + vec3(0, 0, 0)), hash(p + vec3(1, 0, 0)), f.x),
            mix(hash(p + vec3(0, 1, 0)), hash(p + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(hash(p + vec3(0, 0, 1)), hash(p + vec3(1, 0, 1)), f.x),
            mix(hash(p + vec3(0, 1, 1)), hash(p + vec3(1, 1, 1)), f.x), f.y),
        f.z);
    return n * 2.0 - 1.0;
}

// 3D swirl (seamless, replaces the original 2D swirl)
vec3 swirl(vec3 p) {
    return vec3(
        noise(p + vec3(0.0, 0.0, 0.33)),
        noise(p.yzx + vec3(0.0, 0.0, 0.66)),
        noise(p.zxy + vec3(0.0, 0.0, 0.99))
    );
}

// swirled, domain-warped marble field in [0, 1] over the sky sphere
float marble(vec3 p, float t) {
    p += 0.6 * swirl(2.0 * p + t * 0.15);
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * (0.5 + 0.5 * noise(p + vec3(0.0, 0.0, t * 0.12)));
        p = p * 2.0 + swirl(p + t * 0.04);
        a *= 0.5;
    }
    return clamp(v, 0.0, 1.0);
}

vec4 renderLava(vec2 iResolution, float iTime) {
    // reconstruct world-space view ray -> world-fixed, seamless sky
    vec2 ndc = (gl_FragCoord.xy / iResolution) * 2.0 - 1.0;
    vec4 viewPos = invProj * vec4(ndc, 1.0, 1.0);
    viewPos /= viewPos.w;
    vec3 dir = normalize((invView * vec4(viewPos.xyz, 0.0)).xyz);

    float m = marble(dir * 2.5, iTime);
    m = pow(m, 1.4);

    // hot lava palette: deep red -> orange -> yellow -> white-hot cores
    vec3 col = vec3(0.28, 0.02, 0.0);
    col = mix(col, vec3(0.85, 0.10, 0.0), smoothstep(0.10, 0.45, m));
    col = mix(col, vec3(1.0, 0.50, 0.0), smoothstep(0.40, 0.70, m));
    col = mix(col, vec3(1.0, 0.90, 0.25), smoothstep(0.65, 0.92, m));
    col += vec3(1.0, 0.95, 0.7) * smoothstep(0.90, 1.0, m);

    // overall glow lift
    col += vec3(0.15, 0.03, 0.0) * m;

    return vec4(clamp(col, 0.0, 1.0), 1.0);
}

// ---------------------------------------------------------------------------
//  "Eyes" sky shader
//  Original Shadertoy: Felipe Tovar-Henao [www.felipe-tovar-henao.com]
//  Animated eye mosaic using value noise and shaping functions. Transcribed
//  from the original mainImage into a screen-space pass (no world ray needed).
// ---------------------------------------------------------------------------

/* -------- SHAPERS/MISC -------- */
float eFold(in float x) {
    return abs(mod(x + 1.0, 2.0) - 1.0);
}

float reliRamp(in float x, in float s) {
    return floor(x) + clamp((max(1.0, s) * (fract(x) - 0.5)) + 0.5, 0.0, 1.0);
}

float cosine_ramp(in float x, in float s) {
    float y = cos(fract(x) * PI);
    return floor(x) + 0.5 - (0.5 * pow(abs(y), 1.0 / s) * sign(y));
}

float camel_ramp(in float x, in float s) {
    float y = fract(x);
    return floor(x) + pow(0.5 - (0.5 * cos(TWO_PI * y) * cos(PI * y)), s);
}

vec2 eRotate2D(in vec2 vUV, in float theta) {
    return vUV * mat2(cos(theta), -sin(theta), sin(theta), cos(theta));
}

float eScale(in float x, in float inmin, in float inmax, in float outmin, in float outmax) {
    return ((x - inmin) / (inmax - inmin)) * (outmax - outmin) + outmin;
}

/* -------- NOISE -------- */
float random1D(in vec2 vUV, in int seed) {
    return fract(abs(sin(dot(vUV, vec2(11.13, 57.05)) + float(seed)) * 48240.41));
}

float value_noise(in vec2 vUV, in int seed) {
    vec2 x = floor(vUV);
    vec2 m = fract(vUV);

    float bl = random1D(x, seed);
    float br = random1D(x + vec2(1.0, 0.0), seed);
    float tl = random1D(x + vec2(0.0, 1.0), seed);
    float tr = random1D(x + vec2(1.0, 1.0), seed);

    vec2 cf = smoothstep(vec2(0.0), vec2(1.0), m);

    float tm = mix(tl, tr, cf.x);
    float bm = mix(bl, br, cf.x);

    return mix(bm, tm, cf.y);
}

/* -------- EYE FUNCTIONS -------- */
float eyeSDF(in vec2 vUV, in float s) {
    float o = 0.125;
    vec2 uv = abs(vUV * vec2(1.0 + o, 1.0));
    float x = clamp(uv.x * (1.0 - o), 0.0, 0.5);
    uv -= vec2(0.5, pow(cos(x * PI) / s, s));
    return length(max(vec2(0.0), uv)) + min(0.0, max(uv.x, uv.y));
}

vec4 mk_tearduct(in float sdf, in float t) {
    vec3 col = mix(vec3(0.0), vec3(0.8471), cosine_ramp(eFold(sdf * 60.0 + t * 0.25), 2.0));
    float a = smoothstep(EYE_EDGE, 0.0, sdf + EYE_EDGE);
    return vec4(col, a);
}

vec4 mk_eyelids(in float sdf) {
    return smoothstep(EYE_EDGE * 2.0, 0.0, abs(sdf) - 0.01) * vec4(0.6471, 0.6471, 0.6471, 1.0);
}

vec4 mk_sclera(in vec2 vUV, in float d, in float t) {
    float g = eRotate2D(vUV, length(vUV) * TWO_PI * sin(t * 0.1) + t * 0.01).y;
    vec3 glow = smoothstep(EYE_EDGE, 0.0, g) * vec3(1.0);
    vec4 sclera = smoothstep(EYE_EDGE, 0.0, d - 0.25) * vec4(0.8275, 0.8235, 0.8235, 1.0);
    vec4 border = smoothstep(EYE_EDGE, 0.0, abs(d - 0.25) - 0.0025) * vec4(0.2549, 0.2549, 0.2549, 1.0);
    sclera.rgb = mix(sclera.rgb, glow, glow.r);
    sclera = mix(sclera, border, border.a);
    return sclera;
}

vec4 mk_iris(in vec2 vUV, in float d, in float t) {
    float a = atan(vUV.x, vUV.y);
    vec3 col = mix(vec3(0.6784, 0.7922, 0.8431), vec3(0.6118, 0.7255, 0.7804), eFold(cosine_ramp(sin(a * 3.0 * cos(a * 2.0) + t * 0.5), 4.0)));
    col = mix(col, vec3(0.7765, 0.8196, 0.8392), cosine_ramp(cos(3.0 * a * sin(-a * 1.5) + t * 0.4) * 0.5 + 0.5, 4.0));
    vec4 iris = smoothstep(EYE_EDGE, 0.0, d - 0.125) * vec4(col, 1.0);
    vec4 border = smoothstep(EYE_EDGE, 0.0, abs(d - 0.125) - 0.002) * vec4(0.2627, 0.2353, 0.2353, 1.0);

    float shade = cos(a + t * 0.25) * 0.5 + 0.5;
    shade *= shade;
    shade = cosine_ramp(shade, 4.0);
    iris = mix(iris, border, border.a);
    iris.rgb = mix(iris.rgb, vec3(0.3529, 0.4627, 0.4941), shade * 0.75);

    return iris;
}

vec4 mk_pupil(in float d, in float t) {
    t = sin(d + t * 0.125) * 0.01;
    return smoothstep(EYE_EDGE, 0.0, d - 0.05 + t) * vec4(0.0627, 0.0588, 0.0588, 1.0);
}

vec4 mk_glow(in vec2 vUV, in float t) {
    float d = length(vUV);
    vUV *= vec2(sin(d * 2.123 - t * 0.798347), cos(d * 3.123 + t * 0.91823)) * 0.1 + 1.0;
    d = length((vUV - (vUV.y * 0.1)) - 0.05);

    vec4 glow = smoothstep(EYE_EDGE * 1.5, 0.0, d - 0.03) * vec4(1.0);

    d = length((vUV - (vUV.y * 0.1)) + 0.05);
    glow = mix(glow, smoothstep(EYE_EDGE * 1.25, 0.0, d - 0.02) * vec4(1.0), 1.0 - glow.a);

    return glow;
}

vec4 mk_retina(in vec2 vUV, in float t) {
    vec4 retina = vec4(0.0);
    vUV *= length(vUV) * 1.5 + 1.0;
    vUV += vec2(cos(t * 0.98), sin(t * 0.234)) * 0.08;
    float d = length(vUV);
    vec4 glow = mk_glow(vUV, t);
    vec4 iris = mk_iris(vUV, d, t);
    vec4 pupil = mk_pupil(d + sin(t * 0.5 + 0.12) * 0.005, t);

    retina = mix(retina, iris, iris.a);
    retina = mix(retina, pupil, pupil.a * retina.a);
    retina = mix(retina, glow, glow.a * 0.975 * iris.a);

    return retina;
}

vec4 mk_eyeball(in vec2 vUV, in float t) {
    float d = length(vUV);
    vec4 eyeball = vec4(0.0);
    vec4 sclera = mk_sclera(vUV, d, t);
    vec4 retina = mk_retina(vUV, t + reliRamp(t, 2.0));

    eyeball = mix(eyeball, sclera, sclera.a);
    eyeball = mix(eyeball, retina, retina.a * sclera.a);

    return eyeball;
}

vec4 mk_eye(in vec2 vUV, in float b, in float t) {
    vec4 eye = vec4(0.0);
    float eye_sdf = eyeSDF(vUV * 1.06, b);
    vec4 tearduct = mk_tearduct(eye_sdf, t);
    vec4 eyelids = mk_eyelids(eye_sdf);
    vec4 eyeball = mk_eyeball(vUV, t);

    eye = mix(eye, tearduct, tearduct.a);
    eye = mix(eye, eyeball, eyeball.a * tearduct.a);
    eye = mix(eye, eyelids, eyelids.a);

    return vec4(eye);
}

// One flat tiled eye field, evaluated over an arbitrary 2D coordinate plane.
vec4 eyeField(in vec2 vUV, in float iTime) {
    float scl = 1.75;
    vec2 pUV = vUV * scl + vec2(iTime * 0.01, iTime * -0.0107);
    vec4 color = vec4(0.0);
    float sdf = 1.0;

    for (float i = 0.0; i <= 1.0; i++) {
        vec2 q = pUV + 7.5 * (1.0 - i);
        vec2 iUV = floor(q);
        q = fract(q) - 0.5;
        float rand = value_noise(iUV, int(q.x * q.y));
        float t = (iTime + 100.0 * (i + 0.5)) * (rand + 1.0);
        q = eRotate2D(q, t * 0.01);
        float b = eScale(pow(eFold(t * 0.1), 100.0), 0.0, 1.0, 2.0, 10.0);
        vec4 eye = mk_eye(q * pow(2.0, rand), b, t * 0.5);
        sdf = min(sdf, eyeSDF(q * scl, b));
        color = mix(color, eye, eye.a);
    }

    sdf = camel_ramp(eFold(sdf * (16.0 + sin(iTime * 0.25) * 2.0) - iTime * 0.1), 1.0);
    sdf *= sdf;
    color = mix(color, sdf * vec4(0.6824, 0.6824, 0.6824, 1.0), sdf * (1.0 - color.a));
    return color;
}

vec4 renderEyes(vec2 iResolution, float iTime) {
    // reconstruct the world-space view ray so the eyes stay fixed in the world
    // (instead of being pasted to the screen and swimming with the camera).
    vec2 ndc = (gl_FragCoord.xy / iResolution) * 2.0 - 1.0;
    vec4 viewPos = invProj * vec4(ndc, 1.0, 1.0);
    viewPos /= viewPos.w;
    vec3 dir = normalize((invView * vec4(viewPos.xyz, 0.0)).xyz);

    // Stereographic projection of the view ray onto a single seamless plane.
    // The one unavoidable singularity is placed straight DOWN (the nadir),
    // where it's hidden below the horizon by the terrain. Because stereographic
    // mapping is conformal, eyes stay round across the whole visible sky -
    // no cube seams, no triangle/square squashing, no pole pinch overhead.
    float denom = max(1.0 + dir.y, 0.05); // -> 0 only when looking straight down
    vec2 vUV = dir.xz / denom;

    vec4 color = eyeField(vUV * 2.0, iTime);
    return vec4(color.rgb, 1.0);
}

// ---------------------------------------------------------------------------
//  "Cosmo" sky shader
//  Shadertoy composite (Star Nest volumetric field by Kali + oil noise +
//  procedural glow layers). Screen-space; transcribed verbatim into a function.
// ---------------------------------------------------------------------------
#define iterations 13
#define formuparam 0.53
#define volsteps 20
#define stepsize 0.1
#define zoom   0.800
#define tile   0.850
#define brightness 0.0015
#define darkmatter 0.300
#define distfading 0.730
#define saturation 0.850
#define TWOPI 6.283184
#define D2R PI/180.0*
#define OC 15.0

mat2 rotMat(in float r){float c = cos(r);float s = sin(r);return mat2(c,-s,s,c);}

float abs1d(in float x){return abs(fract(x)-0.5);}
vec2 abs2d(in vec2 v){return abs(fract(v)-0.5);}
float cos1d(float p){ return cos(p*TWOPI)*0.25+0.25;}
float sin1d(float p){ return sin(p*TWOPI)*0.25+0.25;}

vec3 Oilnoise(in vec2 pos, in vec3 RGB)
{
    vec2 q = vec2(0.0);
    float result = 0.0;

    float s = 2.2;
    float gain = 0.44;
    vec2 aPos = abs2d(pos)*0.5;

    for(float i = 0.0; i < OC; i++)
    {
        pos *= rotMat(D2R 30.);
        float time = (sin(iTime)*0.5+0.5)*0.2+iTime*0.8;
        q =  pos * s + time;
        q =  pos * s + aPos + time;
        q = vec2(cos(q));

        result += sin1d(dot(q, vec2(0.3))) * gain;

        s *= 1.07;
        aPos += cos(smoothstep(0.0,0.15,q));
        aPos*= rotMat(D2R 5.0);
        aPos*= 1.232;
    }

    result = pow(result,4.504);
    return clamp( RGB / abs1d(dot(q, vec2(-0.240,0.000)))*.5 / result, vec3(0.0), vec3(1.0));
}

float easeFade(float x)
{
    return 1.-(2.*x-1.)*(2.*x-1.)*(2.*x-1.)*(2.*x-1.);
}
float holeFade(float t, float life, float lo)
{
    return easeFade(mod(t-lo,life)/life);
}
vec2 getPos(float t, float life, float offset, float lo)
{
    return vec2(cos(offset+floor((t-lo)/life)*life)*iResolution.x/2.,
    sin(2.*offset+floor((t-lo)/life)*life)*iResolution.y/2.);
}

void mainVR( out vec4 fragColor, in vec2 fragCoord, in vec3 ro, in vec3 rd )
{
    vec3 dir=rd;
    vec3 from=ro;

    float s=0.1,fade=1.;
    vec3 v=vec3(0.);
    for (int r=0; r<volsteps; r++) {
        vec3 p=from+s*dir*.5;
        p = abs(vec3(tile)-mod(p,vec3(tile*2.)));
        float pa,a=pa=0.;
        for (int i=0; i<iterations; i++) {
            p=abs(p)/dot(p,p)-formuparam;
            p.xy*=mat2(cos(iTime*0.01),sin(iTime*0.01),-sin(iTime*0.01),cos(iTime*0.01) );
            a+=abs(length(p)-pa);
            pa=length(p);
        }
        float dm=max(0.,darkmatter-a*a*.001);
        a*=a*a;
        if (r>6) fade*=1.3-dm;
        v+=fade;
        v+=vec3(s,s*s,s*s*s*s)*a*brightness*fade;
        fade*=distfading;
        s+=stepsize;
    }
    v=mix(vec3(length(v)),v,saturation);
    fragColor = vec4(v*.01,1.);
}

#define r(a) mat2(cos(a + asin(vec4(0,1,-1,0))))
#define Q(p) p *= 2.*r(round(atan(p.x, p.y) * 4.) / 4.)

vec4 renderCosmo()
{
    // World-fixed coordinate: reconstruct the view ray and project it
    // stereographically (the single singularity hidden straight down, below
    // the horizon). Everything in this composite is a function of fragCoord, so
    // feeding it a world-locked virtual fragCoord makes the whole effect static
    // instead of swimming with the camera.
    vec2 ndc = (gl_FragCoord.xy / iResolution) * 2.0 - 1.0;
    vec4 viewPos = invProj * vec4(ndc, 1.0, 1.0);
    viewPos /= viewPos.w;
    vec3 wdir = normalize((invView * vec4(viewPos.xyz, 0.0)).xyz);
    float denom = max(1.0 + wdir.y, 0.05);
    vec2 fragCoord = (wdir.xz / denom * 0.5 + 0.5) * iResolution;

    vec4 fragColor = vec4(0.0);

    vec4 o = fragColor;
    vec2 u = fragCoord;
    vec2 uv=fragCoord.xy/iResolution.xy-.5;
    vec2 cPos = -1.0 + 2.0 * fragCoord.xy / iResolution.xy;
    float cLength = length(cPos);

    vec2 st = (fragCoord/iResolution.xy);
    st.x = ((st.x - 0.5) *(iResolution.x / iResolution.y)) + 0.5;

    st*=3.;

    vec3 rgb = vec3(0.30, .8, 1.200);

    float AA = 1.0;
    vec2 pix = 1.0 / iResolution.xy;
    vec2 aaST = vec2(0.0);
    vec3 col;
    for(float i = 0.0; i < AA; i++)
    {
        for(float j = 0.0; j < AA; j++)
        {
            aaST = st + pix * vec2( (i+0.5)/AA, (j+0.5)/AA );
            col += Oilnoise(aaST, rgb);
        }
    }
    col /= AA * AA;

    uv.y*=iResolution.y/iResolution.x;
    vec2 v = iResolution.xy,
         w,
         k = u = .2*(u+u-v)/v.y;

    o = vec4(1,2,3,0);

    for (float a = .5, t = iTime*0.21, i;
         ++i < 19.;
         o += (1.+ cos(vec4(0,1,3,0)+t))
           / length((1.+i*dot(v,v)) * sin(w*3.-9.*u.yx+t))
         )
        v = cos(++t - 7.*u*pow(a += .03, i)) - 5.*u,
        u *= mat2(cos(i+t*.02 - vec4(0,11,33,0))),
        u += .005 * tanh(40.*dot(u,u)*cos(1e2*u.yx+t))
           + .2 * a * u
           + .003 * cos(t+4.*exp(-.01*dot(o,o))),
        w = u / (1. -2.*dot(u,u));

    o = pow(o = 1.-sqrt(exp(-o*o*o/2e2)), .3*o/o)
      - dot(k-=u,k) / 250.;
    vec3 dir=vec3(uv*zoom,1.);

    vec2 coord = fragCoord * 2. - iResolution.xy;

    float holeSize = iResolution.y/10.;
    float holeLife = 2.;

    vec3 final;
    for (int i = 0; i<45; i++) {
        vec3 col = 0.5 + 0.5*cos(iTime+uv.xyx+vec3(float(i),2.*float(i)+4.,4.*float(i)+16.));

        float s = holeSize;
        float lifeOffset = float(i)/2.;

        vec2 pos = getPos(iTime, holeLife, float(i)*4.5,lifeOffset);

        float d = distance(coord,pos)/s;
        d = 1./d-.1;

        final += mix(vec3(0),col, d)*holeFade(iTime,holeLife,lifeOffset);
    }
    vec2 pos = 0.5 - uv;
    pos.y /= iResolution.x/iResolution.y;

    float dist = 1.0/length(pos);
    dist *= 0.1;
    dist = pow(dist, 0.8);

    vec3 col2 = dist * vec3(1.0, 2.5, 1.25);
    col2 = 1.0 - exp( -col );

    vec3 from=vec3(1.,.5,0.5);
    Q(from.xy);
    from.xy+= (cPos/cLength)*cos(cLength*8.0-iTime*2.0) * 0.03;

    mainVR(fragColor, fragCoord, from, dir);

    fragColor*=vec4(final*vec3(0.4,1.,1.)+o.xyz,1.);
    fragColor+=vec4(col2,1.);
    return fragColor;
}

// ---------------------------------------------------------------------------
//  "Cosmo2" sky shader
//  "Accretion" by @XorDev (https://x.com/XorDev/status/1936884244128661986).
//  The original builds its ray from fragCoord; here that single ray is replaced
//  by the world-space view ray so the accretion disk stays fixed in the world.
// ---------------------------------------------------------------------------
vec4 renderCosmo2()
{
    // world-space view ray -> world-fixed (doesn't swim with the camera)
    vec2 ndc = (gl_FragCoord.xy / iResolution) * 2.0 - 1.0;
    vec4 viewPos = invProj * vec4(ndc, 1.0, 1.0);
    viewPos /= viewPos.w;
    vec3 wdir = normalize((invView * vec4(viewPos.xyz, 0.0)).xyz);

    // x = "bubble" wobble amount (turbulence amplitude),
    // y = number of dark "black holes" (1 or 2).
    float wobble = iParams.x;
    float holes = iParams.y;

    // count == 1 -> a single disk, tilted so it's seen from above at an angle;
    // count == 2 -> the default two-lobed look (no tilt).
    vec3 rdir = wdir;
    if (holes < 1.5) {
        float a = 0.8;
        float ca = cos(a), sa = sin(a);
        rdir.yz = mat2(ca, -sa, sa, ca) * rdir.yz;
    }

    vec4 O = vec4(0.0);
    float z = 0.0, d, i = 0.0;

    // raymarch 20 steps
    for (; i++ < 2e1; )
    {
        // sample point along the (world-fixed) ray
        vec3 p = z * rdir + .1;

        // polar coordinates; the angular multiplier sets the number of lobes
        p = vec3(atan(p.y / .2, p.x) * holes, p.z / 3., length(p.xy) - 5. - z * .2);

        // turbulence + refraction (wobble scales how much the bubbles swell)
        for (d = 0.; d++ < 7.;)
            p += wobble * sin(p.yzx * d + iTime + .3 * i) / d;

        // distance to cylinder and waves with refraction
        z += d = length(vec4(.4 * cos(p) - .4, p.z));

        // coloring and brightness
        O += (1. + cos(p.x + i * .4 + z + vec4(6, 1, 2, 0))) / d;
    }

    // tanh tonemap
    return tanh(O * O / 4e2);
}

void main() {
    iResolution = iResolutionTime.xy;
    iTime = iResolutionTime.z;
    float mode = iResolutionTime.w;

    if (mode > 2.5) {
        fragColor = renderCosmo2();
    } else if (mode > 1.5) {
        fragColor = renderCosmo();
    } else if (mode > 0.5) {
        fragColor = renderEyes(iResolution, iTime);
    } else {
        fragColor = renderLava(iResolution, iTime);
    }
}
