package com.jetbrains.packagesearch.intellij.plugin.ui.util

import java.awt.Component
import javax.swing.JPopupMenu
import javax.swing.SwingConstants

internal fun JPopupMenu.showUnderneath(target: Component? = invoker, alignEdge: Int = SwingConstants.LEFT) {
    require(target != null) { "The popup menu must be anchored to an invoker, or have a non-null target" }

    val x = when (alignEdge) {
        SwingConstants.LEFT -> 0
        SwingConstants.RIGHT -> target.width - width
        else -> throw IllegalArgumentException("Only SwingConstants.LEFT and SwingConstants.RIGHT alignments are supported")
    }
    show(target, x, target.height)
}
