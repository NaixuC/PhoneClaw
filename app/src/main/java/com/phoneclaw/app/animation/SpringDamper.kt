package com.phoneclaw.app.animation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import kotlin.math.abs
import kotlin.math.exp

/**
 * 二阶弹簧阻尼系统求解器
 * 传递函数: G(s) = ωn² / (s² + 2ζωn·s + ωn²)
 *
 * @param zeta 阻尼比 ζ — 控制震荡特性
 *   - ζ > 1: 过阻尼 (缓慢稳重)
 *   - ζ = 1: 临界阻尼 (最快无震荡)
 *   - 0 < ζ < 1: 欠阻尼 (弹性灵动)
 *   - ζ = 0: 无阻尼 (持续震荡)
 * @param omegaN 自然频率 ωn — 控制响应速度，越大越快
 * @param initialValue 初始位置
 * @param epsilon 收敛阈值
 */
class SpringDamper(
    var zeta: Float = 0.7f,
    var omegaN: Float = 12f,
    initialValue: Float = 0f,
    private val epsilon: Float = 0.001f,
) {
    var x: Float = initialValue
        private set
    var v: Float = 0f
        private set

    private var lastTarget: Float = initialValue

    /**
     * 更新物理模拟一帧
     * @param dt 帧时间差 (秒)
     * @param target 目标值
     * @return 当前位置 x(t)
     */
    fun update(dt: Float, target: Float): Float {
        // 半隐式欧拉积分
        val dx = target - x
        val acceleration = omegaN * omegaN * dx - 2f * zeta * omegaN * v
        v += acceleration * dt.coerceIn(0f, 0.05f)
        x += v * dt.coerceIn(0f, 0.05f)
        lastTarget = target
        return x
    }

    /**
     * 判断是否已经稳定 (接近目标且速度接近0)
     */
    fun isSettled(target: Float): Boolean {
        return abs(target - x) < epsilon && abs(v) < epsilon
    }

    /**
     * 重置状态
     */
    fun reset(value: Float = 0f) {
        x = value
        v = 0f
        lastTarget = value
    }

    /**
     * 设置新的弹簧参数
     */
    fun configure(zeta: Float, omegaN: Float) {
        this.zeta = zeta
        this.omegaN = omegaN
    }
}

/**
 * Compose AnimationSpec 实现 — 方便直接用在 Compose animate*AsState 中
 */
class SpringDamperSpec(
    val zeta: Float = 0.7f,
    val omegaN: Float = 12f,
) : AnimationSpec<Float> {
    override fun <V> vectorize(
        type: androidx.compose.animation.core.VectorConverter<V>,
        targetValue: V,
        targetVelocity: V?,
    ): androidx.compose.animation.core.VectorizedAnimationSpec<V> {
        return SpringVectorizedSpec(zeta, omegaN)
    }
}

private class SpringVectorizedSpec<V>(
    private val zeta: Float,
    private val omegaN: Float,
) : androidx.compose.animation.core.VectorizedAnimationSpec<V> {
    private val damper = SpringDamper(zeta, omegaN)

    @Suppress("UNCHECKED_CAST")
    override fun getValueFromNanos(
        previousValue: V,
        targetValue: V,
        initialVelocity: V,
        startTimeNanos: Long,
        durationNanos: Long,
    ): V {
        if (previousValue is Float && targetValue is Float) {
            val dt = (durationNanos - startTimeNanos).coerceAtMost(50_000_000L) / 1_000_000_000f
            if (dt > 0) {
                return damper.update(dt, targetValue) as V
            }
        }
        return targetValue
    }

    override fun getVelocityFromNanos(
        previousValue: V, targetValue: V, initialVelocity: V,
        startTimeNanos: Long, durationNanos: Long,
    ): V = initialVelocity

    override val durationNanos: Long get() = Long.MAX_VALUE
    override fun isInfinite(): Boolean = true
}
