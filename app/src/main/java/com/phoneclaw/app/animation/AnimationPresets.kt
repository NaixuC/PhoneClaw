package com.phoneclaw.app.animation

/**
 * 预设动画参数集合
 * 每种预设对应不同的阻尼比 ζ 和自然频率 ωn 组合
 */
object AnimationPresets {
    /**
     * 快速干脆 — 适合按钮点击、开关切换
     * 接近临界阻尼，响应极快
     */
    val Snappy = SpringDamperParams(zeta = 0.9f, omegaN = 20f)

    /**
     * 流畅自然 — 适合列表滚动、页面转场
     * 轻微欠阻尼，有舒适的跟随感
     */
    val Smooth = SpringDamperParams(zeta = 0.8f, omegaN = 10f)

    /**
     * 弹性活泼 — 适合卡片弹入、点赞动画
     * 明显欠阻尼，有弹性效果
     */
    val Bouncy = SpringDamperParams(zeta = 0.4f, omegaN = 8f)

    /**
     * 稳重舒缓 — 适合弹窗出现、通知
     * 过阻尼，没有超调
     */
    val Gentle = SpringDamperParams(zeta = 1.2f, omegaN = 6f)

    /**
     * 轻盈漂浮 — 适合悬浮窗、拖拽
     * 低频率欠阻尼，有漂浮感
     */
    val Float = SpringDamperParams(zeta = 0.6f, omegaN = 5f)

    /**
     * 丝滑柔顺 — 适合背景渐变、色彩过渡
     * 高阻尼低频，极其平滑
     */
    val Silky = SpringDamperParams(zeta = 1.5f, omegaN = 4f)

    /**
     * 回弹效果 — 适合列表到底、边界反馈
     * 低阻尼高频，明显回弹
     */
    val Overshoot = SpringDamperParams(zeta = 0.3f, omegaN = 15f)
}

data class SpringDamperParams(
    val zeta: Float,
    val omegaN: Float,
) {
    fun toSpec() = SpringDamperSpec(zeta, omegaN)
    fun toDamper(initialValue: Float = 0f) = SpringDamper(zeta, omegaN, initialValue)
}
