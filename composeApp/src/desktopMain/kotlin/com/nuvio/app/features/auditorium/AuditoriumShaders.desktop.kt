package com.nuvio.app.features.auditorium
// FORK (Spyder): Nick's Auditorium, not in UmbraProjects. See CLAUDE.md.

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/*
 * fork:auditorium - the hall's passes as Skia runtime shaders, the same pattern as LaunchWash in
 * NuvioGradients.desktop.kt: each SkSL source is constant and compiled once (lazy), each brush is a value
 * (data class equality) that only sets uniforms. `coord` is in the canvas's local pixels, so every position comes in
 * as a pixel uniform. Loops have constant bounds, arrays are indexed by loop indices only, and every shader returns
 * premultiplied colour.
 *
 * The noise is a fract-only hash (Dave Hoskins' hash12): `sin`-based hashes lose precision on GPUs at the pixel
 * coordinates of a 4K view and show up as blocks.
 */
internal const val AUDITORIUM_NOISE_SKSL = """
float hash12(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + float2(1.0, 0.0));
    float c = hash12(i + float2(0.0, 1.0));
    float d = hash12(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm3(float2 p) {
    float2 q = p;
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        s += a * vnoise(q);
        q = q * 2.03 + float2(17.1, 9.3);
        a *= 0.5;
    }
    return s / 0.875;
}
"""

/**
 * The light map. Multiplied over the lit hall: ambient house light with a soft room falloff, the screen's spill
 * (falling off with the distance from the screen's rectangle), a warm wash on the curtains, four wall-light pools, and
 * full light where the picture shows between the curtains or above `openAbove`.
 */
internal const val AUDITORIUM_LIGHT_SKSL = """
uniform float2 size;
uniform float2 screenPos;
uniform float2 screenSize;
uniform float leadLT;
uniform float leadLB;
uniform float leadRT;
uniform float leadRB;
uniform float curtainTop;
uniform float curtainBottom;
uniform float ambient;
uniform float3 ambientTint;
uniform float spill;
uniform float3 spillTint;
uniform float stage;
uniform float3 stageTint;
uniform float2 stageCenter;
uniform float2 stageRadius;
uniform float sconce;
uniform float3 sconceTint;
uniform float sconceReach;
uniform float sconceX[4];
uniform float sconceY[4];
uniform float openAbove;

half4 main(float2 p) {
    float2 uv = p / max(size, float2(1.0));
    float2 cv = (uv - float2(0.5, 0.45)) * float2(1.05, 1.25);
    float room = clamp(1.0 - 0.96 * dot(cv, cv), 0.2, 1.0);
    float3 light = ambientTint * (ambient * room);

    float2 c = screenPos + screenSize * 0.5;
    float2 q = abs(p - c) - screenSize * 0.5;
    float outside = length(max(q, float2(0.0)));
    float dd = outside / max(screenSize.y, 1.0);
    light += spillTint * (spill / (1.0 + 7.0 * dd * dd));

    float2 sv = (p - stageCenter) / max(stageRadius, float2(1.0));
    light += stageTint * (stage * exp(-dot(sv, sv) * 1.3));

    float reach = max(sconceReach, 1.0);
    for (int i = 0; i < 4; i++) {
        float2 v = (p - float2(sconceX[i], sconceY[i])) / float2(reach * 0.75, reach * 1.5);
        light += sconceTint * (sconce * exp(-dot(v, v)));
    }

    float t = clamp((p.y - curtainTop) / max(curtainBottom - curtainTop, 1.0), 0.0, 1.0);
    float l = max(mix(leadLT, leadLB, t), screenPos.x);
    float r = min(mix(leadRT, leadRB, t), screenPos.x + screenSize.x);
    float gx = smoothstep(l - 0.75, l + 0.75, p.x) * (1.0 - smoothstep(r - 0.75, r + 0.75, p.x));
    float sb = screenPos.y + screenSize.y;
    float gy = smoothstep(screenPos.y - 0.75, screenPos.y + 0.75, p.y) * (1.0 - smoothstep(sb - 0.75, sb + 0.75, p.y));
    float sky = 1.0 - smoothstep(openAbove - 1.5, openAbove + 1.5, p.y);
    light = mix(min(light, float3(1.0)), float3(1.0), max(gx * gy, sky));

    float n = fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715))));
    light = clamp(light + (n - 0.5) / 255.0, 0.0, 1.0);
    return half4(half3(light), 1.0);
}
"""

/**
 * The projector beam. The cone runs from `apex` to the screen's top corners and then down the screen's sides, so it
 * lands exactly on the screen. Density thins with distance from the port, slow angular streaks and three octaves of
 * drifting haze break it up, and dust motes (one candidate per grid cell, each wobbling on its own slow orbit, the
 * whole field drifting) catch the light only inside it.
 */
internal const val AUDITORIUM_BEAM_SKSL = AUDITORIUM_NOISE_SKSL + """
uniform float2 apex;
uniform float2 screenPos;
uniform float2 screenSize;
uniform float intensity;
uniform float time;
uniform float px;
uniform float3 tint;

half4 main(float2 p) {
    float dy = p.y - apex.y;
    if (dy <= 1.0 || intensity <= 0.0) {
        return half4(0.0);
    }
    float halfW = screenSize.x * 0.5;
    float slope = halfW / max(screenPos.y - apex.y, 1.0);
    float width = min(slope * dy, halfW);
    float n = (p.x - apex.x) / max(width, 1.0);
    float an = abs(n);
    float cone = 1.0 - smoothstep(0.86, 1.04, an);
    float bottom = screenPos.y + screenSize.y;
    cone *= 1.0 - smoothstep(bottom - screenSize.y * 0.04, bottom, p.y);
    if (cone <= 0.0) {
        return half4(0.0);
    }
    float onScreen = smoothstep(screenPos.y - screenSize.y * 0.06, screenPos.y + screenSize.y * 0.45, p.y);
    float reachFade = 1.0 - 0.55 * smoothstep(apex.y, bottom, p.y);
    float core = 0.5 + 0.5 * exp(-an * an * 2.6);
    float streak = 0.62 + 0.76 * vnoise(float2(n * 7.0 + 3.0, time * 0.11));
    float2 hq = p / (max(px, 0.25) * 240.0);
    float haze = 0.30 + 1.25 * fbm3(hq + float2(time * 0.031, -time * 0.017));
    float flicker = 0.975 + 0.025 * sin(time * 41.0) * sin(time * 17.0);
    float a = 0.17 * cone * core * streak * haze * reachFade * flicker * (1.0 - 0.72 * onScreen);

    float cell = 44.0 * max(px, 0.25);
    float2 q = (p + float2(time * 6.0, -time * 2.2) * max(px, 0.25)) / cell;
    float2 base = floor(q);
    float motes = 0.0;
    for (int my = -1; my <= 1; my++) {
        for (int mx = -1; mx <= 1; mx++) {
            float2 c = base + float2(float(mx), float(my));
            if (hash12(c) > 0.26) {
                continue;
            }
            float sp1 = 0.25 + 0.35 * hash12(c + 3.1);
            float sp2 = 0.20 + 0.30 * hash12(c + 5.3);
            float2 wob = float2(
                sin(time * sp1 + 6.2831 * hash12(c + 7.7)),
                cos(time * sp2 + 6.2831 * hash12(c + 1.9))
            ) * 0.32;
            float2 pos = c + float2(0.5, 0.5) + (float2(hash12(c + 11.0), hash12(c + 23.0)) - 0.5) * 0.34 + wob;
            float big = hash12(c + 97.0) > 0.9 ? 2.6 : 1.0;
            float r = (0.55 + 1.2 * hash12(c + 37.0)) * big * max(px, 0.25) / cell;
            float2 dv = q - pos;
            float d2 = dot(dv, dv);
            float tw = 0.45 + 0.55 * (0.5 + 0.5 * sin(time * (0.8 + 2.2 * hash12(c + 53.0)) + 6.2831 * hash12(c + 71.0)));
            motes += exp(-d2 / (r * r)) * tw * (0.35 + 0.65 * hash12(c + 89.0)) / big;
        }
    }
    a += 0.55 * cone * core * motes * (1.0 - 0.6 * onScreen);
    a = clamp(a * intensity, 0.0, 1.0);
    return half4(half3(tint * a), half(a));
}
"""

/** Room haze: two slow layers of value noise, lit by the screen and the house lights, thinner low down. */
internal const val AUDITORIUM_HAZE_SKSL = AUDITORIUM_NOISE_SKSL + """
uniform float2 size;
uniform float2 screenPos;
uniform float2 screenSize;
uniform float density;
uniform float glow;
uniform float ambient;
uniform float time;
uniform float px;
uniform float3 tint;

half4 main(float2 p) {
    if (density <= 0.0) {
        return half4(0.0);
    }
    float2 q = p / (max(px, 0.25) * 420.0);
    float a1 = fbm3(q + float2(time * 0.013, time * 0.004));
    float a2 = fbm3(q * 1.8 + float2(-time * 0.021, time * 0.007) + 5.2);
    float m = smoothstep(0.3, 0.85, a1 * 0.62 + a2 * 0.38);
    float2 c = screenPos + screenSize * 0.5;
    float2 dv = (p - c) / max(screenSize * float2(0.85, 1.1), float2(1.0));
    float lit = glow / (1.0 + dot(dv, dv) * 2.2) + ambient * 0.35;
    float low = 1.0 - 0.5 * smoothstep(0.55, 1.0, p.y / max(size.y, 1.0));
    float a = clamp(density * m * lit * low * 0.10, 0.0, 1.0);
    return half4(half3(tint * a), half(a));
}
"""

/**
 * Velvet. `u` runs 0..1 across the fabric from the fixed outer edge to the leading edge (which may lean as it
 * travels), so the folds belong to the cloth and bunch up as the panel narrows. Folds are irregular sums of sines,
 * the faces darken into the valleys, the turning edges carry the velvet's sheen, a fine pile noise breaks up the CG
 * smoothness, and the hem picks up the footlights.
 */
internal const val AUDITORIUM_VELVET_SKSL = AUDITORIUM_NOISE_SKSL + """
uniform float outer;
uniform float leadTop;
uniform float leadBottom;
uniform float top;
uniform float bottom;
uniform float folds;
uniform float depth;
uniform float3 base;
uniform float3 sheen;
uniform float footlight;
uniform float3 footTint;
uniform float px;

half4 main(float2 p) {
    float t = clamp((p.y - top) / max(bottom - top, 1.0), 0.0, 1.0);
    float lead = mix(leadTop, leadBottom, t);
    float den = lead - outer;
    if (abs(den) < 1.0) {
        den = den < 0.0 ? -1.0 : 1.0;
    }
    float u = clamp((p.x - outer) / den, 0.0, 1.0);
    float k = u * folds;
    float ph = k * 6.2831853 + 0.9 * sin(k * 1.37 + 0.7) + 0.4 * sin(k * 3.1 + 2.0) + t * 0.8 * sin(k * 0.9 + 1.3);
    float c = cos(ph);
    float s = sin(ph);
    float facing = 0.5 + 0.5 * c;
    float shade = mix(1.0 - 0.88 * depth, 1.0, pow(facing, 1.4));
    float rim = pow(abs(s), 5.0) * depth;
    float pile = 0.94 + 0.12 * vnoise(float2(k * 9.0, t * 40.0)) + 0.05 * (hash12(floor(p / max(px, 0.5))) - 0.5);
    float3 col = base * ((0.12 + 0.88 * shade) * pile) + sheen * (rim * 0.42 * (0.55 + 0.45 * facing));
    col *= 0.55 + 0.45 * smoothstep(0.0, 0.16, t);
    col += footTint * (footlight * exp(-(1.0 - t) * 7.0) * (0.35 + 0.65 * shade));
    col *= 0.5 + 0.5 * smoothstep(0.0, 0.08, u);
    col *= 1.0 - 0.45 * smoothstep(0.975, 1.0, u);
    col *= 1.0 - 0.55 * smoothstep(0.985, 1.0, t);
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

/**
 * Grain and vignette in one premultiplied layer: the vignette is black at `v`, the grain a white or black speck over
 * it (triangular noise, a new pattern 24 times a second, one grain per 1080p pixel so 4K gets the same texture).
 */
internal const val AUDITORIUM_GRAIN_SKSL = AUDITORIUM_NOISE_SKSL + """
uniform float2 size;
uniform float time;
uniform float grain;
uniform float vignette;
uniform float px;

half4 main(float2 p) {
    float2 uv = p / max(size, float2(1.0));
    float2 d = (uv - 0.5) * float2(size.x / max(size.y, 1.0), 1.0);
    float v = smoothstep(0.42, 1.18, length(d)) * vignette;
    float f = mod(floor(time * 24.0), 97.0);
    float2 g = floor(p / max(px, 1.0)) + float2(f * 17.0, f * 31.0);
    float n = hash12(g) + hash12(g + 0.37) - 1.0;
    float ga = abs(n) * grain;
    float w = n > 0.0 ? 1.0 : 0.0;
    float alpha = ga + v * (1.0 - ga);
    return half4(half3(w * ga), half(alpha));
}
"""

/** The drive-in sky: zenith to a lifted horizon, a faint Milky Way, moonlight off-frame, twinkling stars. */
internal const val AUDITORIUM_SKY_SKSL = AUDITORIUM_NOISE_SKSL + """
uniform float2 size;
uniform float horizon;
uniform float time;
uniform float px;

half4 main(float2 p) {
    float t = clamp(p.y / max(horizon, 1.0), 0.0, 1.0);
    float3 zen = float3(0.008, 0.012, 0.035);
    float3 mid = float3(0.020, 0.032, 0.080);
    float3 hor = float3(0.085, 0.080, 0.125);
    float3 c = mix(zen, mid, smoothstep(0.0, 0.75, t));
    c = mix(c, hor, smoothstep(0.62, 1.0, t));
    float2 uv = p / max(size, float2(1.0));
    float bd = (uv.y - 0.05 - 0.38 * uv.x) * 5.5;
    float sc = max(px, 0.25);
    float band = exp(-bd * bd) * fbm3(p / (sc * 170.0));
    c += float3(0.040, 0.045, 0.065) * band;
    float2 mv = (uv - float2(0.92, -0.10)) * float2(1.0, 1.6);
    c += float3(0.050, 0.058, 0.085) * exp(-dot(mv, mv) * 5.0);
    float cellSize = 38.0 * sc;
    float2 cell = floor(p / cellSize);
    float fade = 1.0 - smoothstep(0.55, 0.95, t);
    for (int sy = -1; sy <= 1; sy++) {
        for (int sx = -1; sx <= 1; sx++) {
            float2 n = cell + float2(float(sx), float(sy));
            if (hash12(n) > 0.30) {
                continue;
            }
            float2 pos = (n + float2(hash12(n + 11.0), hash12(n + 23.0))) * cellSize;
            float radius = (0.45 + 0.9 * hash12(n + 37.0)) * sc;
            float d = distance(p, pos);
            if (d > radius * 4.0) {
                continue;
            }
            float tw = 0.65 + 0.35 * sin(time * (0.7 + 2.5 * hash12(n + 53.0)) + 6.2831 * hash12(n + 71.0));
            float b = (0.12 + 0.88 * pow(hash12(n + 89.0), 3.0)) * tw;
            float3 starTint = mix(float3(0.80, 0.86, 1.0), float3(1.0, 0.90, 0.78), hash12(n + 5.0));
            c += starTint * (exp(-(d * d) / (radius * radius)) * b * fade);
        }
    }
    float nn = fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715))));
    c += (nn - 0.5) / 255.0;
    return half4(half3(c), 1.0);
}
"""

/** Every hall shader by name, for the compile test. */
internal val AUDITORIUM_SKSL_SOURCES: Map<String, String> = mapOf(
    "light" to AUDITORIUM_LIGHT_SKSL,
    "beam" to AUDITORIUM_BEAM_SKSL,
    "haze" to AUDITORIUM_HAZE_SKSL,
    "velvet" to AUDITORIUM_VELVET_SKSL,
    "grain" to AUDITORIUM_GRAIN_SKSL,
    "sky" to AUDITORIUM_SKY_SKSL,
)

private val lightEffect: RuntimeEffect by lazy { RuntimeEffect.makeForShader(AUDITORIUM_LIGHT_SKSL) }
private val beamEffect: RuntimeEffect by lazy { RuntimeEffect.makeForShader(AUDITORIUM_BEAM_SKSL) }
private val hazeEffect: RuntimeEffect by lazy { RuntimeEffect.makeForShader(AUDITORIUM_HAZE_SKSL) }
private val velvetEffect: RuntimeEffect by lazy { RuntimeEffect.makeForShader(AUDITORIUM_VELVET_SKSL) }
private val grainEffect: RuntimeEffect by lazy { RuntimeEffect.makeForShader(AUDITORIUM_GRAIN_SKSL) }
private val skyEffect: RuntimeEffect by lazy { RuntimeEffect.makeForShader(AUDITORIUM_SKY_SKSL) }

private fun RuntimeShaderBuilder.color3(name: String, color: Color) = uniform(name, color.red, color.green, color.blue)

// ---- light ----

internal actual fun auditoriumLightBrush(map: AuditoriumLightMap): Brush = LightMapBrush(map)

private data class LightMapBrush(val map: AuditoriumLightMap) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val m = map
        val b = RuntimeShaderBuilder(lightEffect)
        b.uniform("size", size.width, size.height)
        b.uniform("screenPos", m.screen.left, m.screen.top)
        b.uniform("screenSize", m.screen.width, m.screen.height)
        b.uniform("leadLT", m.leftLeadTop)
        b.uniform("leadLB", m.leftLeadBottom)
        b.uniform("leadRT", m.rightLeadTop)
        b.uniform("leadRB", m.rightLeadBottom)
        b.uniform("curtainTop", m.curtainTop)
        b.uniform("curtainBottom", m.curtainBottom)
        b.uniform("ambient", m.ambient)
        b.color3("ambientTint", m.ambientTint)
        b.uniform("spill", m.spill)
        b.color3("spillTint", m.spillTint)
        b.uniform("stage", m.stage)
        b.color3("stageTint", m.stageTint)
        b.uniform("stageCenter", m.stageArea.center.x, m.stageArea.center.y)
        b.uniform("stageRadius", m.stageArea.width / 2f, m.stageArea.height / 2f)
        b.uniform("sconce", m.sconce)
        b.color3("sconceTint", m.sconceTint)
        b.uniform("sconceReach", m.sconceReach)
        val far = Offset(-100_000f, -100_000f)
        b.uniform("sconceX", FloatArray(4) { (m.sconces.getOrNull(it) ?: far).x })
        b.uniform("sconceY", FloatArray(4) { (m.sconces.getOrNull(it) ?: far).y })
        b.uniform("openAbove", m.openAbove)
        return b.makeShader().asComposeShader()
    }
}

// ---- beam ----

internal actual fun auditoriumBeamBrush(apex: Offset, screen: Rect, intensity: Float, time: Float, px: Float, tint: Color): Brush =
    BeamBrush(apex, screen, intensity, time, px, tint)

private data class BeamBrush(
    val apex: Offset,
    val screen: Rect,
    val intensity: Float,
    val time: Float,
    val px: Float,
    val tint: Color,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val b = RuntimeShaderBuilder(beamEffect)
        b.uniform("apex", apex.x, apex.y)
        b.uniform("screenPos", screen.left, screen.top)
        b.uniform("screenSize", screen.width, screen.height)
        b.uniform("intensity", intensity)
        b.uniform("time", time)
        b.uniform("px", px)
        b.color3("tint", tint)
        return b.makeShader().asComposeShader()
    }
}

// ---- haze ----

internal actual fun auditoriumHazeBrush(
    screen: Rect,
    density: Float,
    glow: Float,
    ambient: Float,
    time: Float,
    px: Float,
    tint: Color,
): Brush = HazeBrush(screen, density, glow, ambient, time, px, tint)

private data class HazeBrush(
    val screen: Rect,
    val density: Float,
    val glow: Float,
    val ambient: Float,
    val time: Float,
    val px: Float,
    val tint: Color,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val b = RuntimeShaderBuilder(hazeEffect)
        b.uniform("size", size.width, size.height)
        b.uniform("screenPos", screen.left, screen.top)
        b.uniform("screenSize", screen.width, screen.height)
        b.uniform("density", density)
        b.uniform("glow", glow)
        b.uniform("ambient", ambient)
        b.uniform("time", time)
        b.uniform("px", px)
        b.color3("tint", tint)
        return b.makeShader().asComposeShader()
    }
}

// ---- velvet ----

internal actual fun auditoriumVelvetBrush(
    outerX: Float,
    leadTopX: Float,
    leadBottomX: Float,
    top: Float,
    bottom: Float,
    folds: Float,
    depth: Float,
    base: Color,
    sheen: Color,
    footlight: Float,
    footTint: Color,
    px: Float,
): Brush = VelvetBrush(outerX, leadTopX, leadBottomX, top, bottom, folds, depth, base, sheen, footlight, footTint, px)

private data class VelvetBrush(
    val outerX: Float,
    val leadTopX: Float,
    val leadBottomX: Float,
    val top: Float,
    val bottom: Float,
    val folds: Float,
    val depth: Float,
    val base: Color,
    val sheen: Color,
    val footlight: Float,
    val footTint: Color,
    val px: Float,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val b = RuntimeShaderBuilder(velvetEffect)
        b.uniform("outer", outerX)
        b.uniform("leadTop", leadTopX)
        b.uniform("leadBottom", leadBottomX)
        b.uniform("top", top)
        b.uniform("bottom", bottom)
        b.uniform("folds", folds)
        b.uniform("depth", depth)
        b.color3("base", base)
        b.color3("sheen", sheen)
        b.uniform("footlight", footlight)
        b.color3("footTint", footTint)
        b.uniform("px", px)
        return b.makeShader().asComposeShader()
    }
}

// ---- grain ----

internal actual fun auditoriumGrainBrush(time: Float, grain: Float, vignette: Float, px: Float): Brush =
    GrainBrush(time, grain, vignette, px)

private data class GrainBrush(val time: Float, val grain: Float, val vignette: Float, val px: Float) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val b = RuntimeShaderBuilder(grainEffect)
        b.uniform("size", size.width, size.height)
        b.uniform("time", time)
        b.uniform("grain", grain)
        b.uniform("vignette", vignette)
        b.uniform("px", px)
        return b.makeShader().asComposeShader()
    }
}

// ---- sky ----

internal actual fun auditoriumSkyBrush(horizon: Float, time: Float, px: Float): Brush = SkyBrush(horizon, time, px)

private data class SkyBrush(val horizon: Float, val time: Float, val px: Float) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val b = RuntimeShaderBuilder(skyEffect)
        b.uniform("size", size.width, size.height)
        b.uniform("horizon", horizon)
        b.uniform("time", time)
        b.uniform("px", px)
        return b.makeShader().asComposeShader()
    }
}
