@file:Suppress("MagicNumber") // Thanks Swing...
package com.jetbrains.packagesearch.intellij.plugin.ui.util

import com.intellij.openapi.util.BuildNumber
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.table.JBTable
import com.jetbrains.packagesearch.intellij.plugin.PluginEnvironment
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.UIManager

@ScaledPixels
internal fun scrollbarWidth() = UIManager.get("ScrollBar.width") as Int

internal fun mouseListener(
    onClick: (e: MouseEvent) -> Unit = {},
    onPressed: (e: MouseEvent) -> Unit = {},
    onReleased: (e: MouseEvent) -> Unit = {},
    onEntered: (e: MouseEvent) -> Unit = {},
    onExited: (e: MouseEvent) -> Unit = {}
) = object : MouseListener {
    override fun mouseClicked(e: MouseEvent) {
        onClick(e)
    }

    override fun mousePressed(e: MouseEvent) {
        onPressed(e)
    }

    override fun mouseReleased(e: MouseEvent) {
        onReleased(e)
    }

    override fun mouseEntered(e: MouseEvent) {
        onEntered(e)
    }

    override fun mouseExited(e: MouseEvent) {
        onExited(e)
    }
}

internal fun JBTable.disableHoverIfNeeded() {
    // There is a bug on IJ 2020.3—2020.3.2 (and maybe later versions) where the hover effect
    // causes item rendering to break. This works around it. See IDEA-260619.
    val buildNumber = PluginEnvironment().ideBuildNumber
    if (buildNumber >= BuildNumber(buildNumber.productCode, 203, 4449)) {
        logDebug { "Disabling hover effect on packages table to avoid rendering artefacts" }
        val field = RenderingUtil::class.java.getDeclaredField("PAINT_HOVERED_BACKGROUND")
            ?: throw IllegalStateException("RenderingUtil#PAINT_HOVERED_BACKGROUND field not found on IJ 203+!")
        val key = field.get(null)
            ?: throw IllegalStateException("RenderingUtil#PAINT_HOVERED_BACKGROUND value not available on IJ 203+!")
        putClientProperty(key, java.lang.Boolean.FALSE)
    }
}
