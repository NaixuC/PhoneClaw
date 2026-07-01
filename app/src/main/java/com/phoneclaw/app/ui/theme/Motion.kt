package com.phoneclaw.app.ui.theme

import androidx.compose.animation.core.tween
import com.phoneclaw.app.animation.AnimationPresets

/**
 * 基于二阶弹簧阻尼模型的全局动画系统
 */
object PhoneMotion {
    val snappy get() = AnimationPresets.Snappy.toSpec()
    val smooth get() = AnimationPresets.Smooth.toSpec()
    val bouncy get() = AnimationPresets.Bouncy.toSpec()
    val gentle get() = AnimationPresets.Gentle.toSpec()
    val float get() = AnimationPresets.Float.toSpec()
    val silky get() = AnimationPresets.Silky.toSpec()

    fun fast() = tween<Float>(200)
    fun normal() = tween<Float>(350)
    fun slow() = tween<Float>(600)
}
