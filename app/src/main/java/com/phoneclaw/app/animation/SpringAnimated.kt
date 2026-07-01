package com.phoneclaw.app.animation

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * 弹簧驱动的 Float 动画
 *
 * @param target 目标值
 * @param params 弹簧参数
 * @param threshold 收敛阈值
 */
@Composable
fun rememberSpringFloat(
    target: Float,
    params: SpringDamperParams = AnimationPresets.Smooth,
    threshold: Float = 0.5f,
): State<Float> {
    val damper = remember(params) {
        SpringDamper(params.zeta, params.omegaN, target)
    }
    val result = remember { mutableFloatStateOf(target) }

    LaunchedEffect(Unit) {
        var lastFrameMs = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { frameTimeMs ->
                if (lastFrameMs == 0L) {
                    lastFrameMs = frameTimeMs
                    return@withInfiniteAnimationFrameMillis
                }
                val dt = ((frameTimeMs - lastFrameMs) / 1000f).coerceIn(0.001f, 0.05f)
                lastFrameMs = frameTimeMs
                result.floatValue = damper.update(dt, target)
            }
        }
    }

    LaunchedEffect(target) {
        if (damper.isSettled(target)) {
            result.floatValue = target
        }
    }

    return result
}

/**
 * 弹簧驱动的 Int 动画
 */
@Composable
fun rememberSpringInt(
    target: Int,
    params: SpringDamperParams = AnimationPresets.Snappy,
): State<Int> {
    val floatTarget = remember(target) { mutableFloatStateOf(target.toFloat()) }
    val floatState = rememberSpringFloat(floatTarget.floatValue, params)
    val result = remember { mutableStateOf(target) }

    LaunchedEffect(floatState.value) {
        result.value = floatState.value.roundToInt()
    }

    LaunchedEffect(target) {
        floatTarget.floatValue = target.toFloat()
    }

    return result
}

/**
 * 弹簧驱动的 Offset (2D位置) 动画
 */
@Composable
fun rememberSpringOffset(
    target: Offset,
    params: SpringDamperParams = AnimationPresets.Float,
): State<Offset> {
    val damperX = remember(params) { SpringDamper(params.zeta, params.omegaN, target.x) }
    val damperY = remember(params) { SpringDamper(params.zeta, params.omegaN, target.y) }
    val result = remember { mutableStateOf(target) }

    LaunchedEffect(Unit) {
        var lastFrameMs = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { frameTimeMs ->
                if (lastFrameMs == 0L) {
                    lastFrameMs = frameTimeMs
                    return@withInfiniteAnimationFrameMillis
                }
                val dt = ((frameTimeMs - lastFrameMs) / 1000f).coerceIn(0.001f, 0.05f)
                lastFrameMs = frameTimeMs
                result.value = Offset(
                    damperX.update(dt, target.x),
                    damperY.update(dt, target.y),
                )
            }
        }
    }
    return result
}

/**
 * 弹簧驱动的 Size 动画
 */
@Composable
fun rememberSpringSize(
    target: Size,
    params: SpringDamperParams = AnimationPresets.Smooth,
): State<Size> {
    val damperW = remember(params) { SpringDamper(params.zeta, params.omegaN, target.width) }
    val damperH = remember(params) { SpringDamper(params.zeta, params.omegaN, target.height) }
    val result = remember { mutableStateOf(target) }

    LaunchedEffect(Unit) {
        var lastFrameMs = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { frameTimeMs ->
                if (lastFrameMs == 0L) {
                    lastFrameMs = frameTimeMs
                    return@withInfiniteAnimationFrameMillis
                }
                val dt = ((frameTimeMs - lastFrameMs) / 1000f).coerceIn(0.001f, 0.05f)
                lastFrameMs = frameTimeMs
                result.value = Size(
                    damperW.update(dt, target.width),
                    damperH.update(dt, target.height),
                )
            }
        }
    }
    return result
}

/**
 * 弹簧驱动的 Color 动画 — 逐通道插值
 */
@Composable
fun rememberSpringColor(
    target: Color,
    params: SpringDamperParams = AnimationPresets.Silky,
): State<Color> {
    val dc = remember(params) {
        SpringColorDamper(params.zeta, params.omegaN, target)
    }
    val result = remember { mutableStateOf(target) }

    LaunchedEffect(Unit) {
        var lastFrameMs = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { frameTimeMs ->
                if (lastFrameMs == 0L) {
                    lastFrameMs = frameTimeMs
                    return@withInfiniteAnimationFrameMillis
                }
                val dt = ((frameTimeMs - lastFrameMs) / 1000f).coerceIn(0.001f, 0.05f)
                lastFrameMs = frameTimeMs
                result.value = dc.update(dt, target)
            }
        }
    }
    return result
}

@Stable
private class SpringColorDamper(zeta: Float, omegaN: Float, initial: Color) {
    private val r = SpringDamper(zeta, omegaN, initial.red)
    private val g = SpringDamper(zeta, omegaN, initial.green)
    private val b = SpringDamper(zeta, omegaN, initial.blue)
    private val a = SpringDamper(zeta, omegaN, initial.alpha)

    fun update(dt: Float, target: Color): Color {
        return Color(
            r.update(dt, target.red),
            g.update(dt, target.green),
            b.update(dt, target.blue),
            a.update(dt, target.alpha),
        )
    }
}
