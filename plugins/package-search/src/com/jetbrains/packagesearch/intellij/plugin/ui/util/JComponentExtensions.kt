package com.jetbrains.packagesearch.intellij.plugin.ui.util

import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal fun JComponent.onRightClick(
    onRightClick: (e: MouseEvent) -> Unit = {}
) = onMouseAction(
    onClick = {
        if (SwingUtilities.isRightMouseButton(it)) onRightClick(it)
    }
)

internal fun JComponent.onMouseAction(
    onClick: (e: MouseEvent) -> Unit = {},
    onPressed: (e: MouseEvent) -> Unit = {},
    onReleased: (e: MouseEvent) -> Unit = {},
    onEntered: (e: MouseEvent) -> Unit = {},
    onExited: (e: MouseEvent) -> Unit = {}
): MouseListener {
    val listener = mouseListener(onClick, onPressed, onReleased, onEntered, onExited)
    addMouseListener(listener)
    return listener
}

internal fun JComponent.onMouseMotion(
    onMouseMoved: (MouseEvent) -> Unit = {},
    onMouseDragged: (MouseEvent) -> Unit = {}
): MouseMotionListener {
    val listener = object : MouseMotionListener {
        override fun mouseDragged(e: MouseEvent) {
            onMouseDragged(e)
        }

        override fun mouseMoved(e: MouseEvent) {
            onMouseMoved(e)
        }
    }
    addMouseMotionListener(listener)
    return listener
}

@ScaledPixels
internal val JComponent.left: Int
    get() = x

@ScaledPixels
internal val JComponent.top: Int
    get() = y

@ScaledPixels
internal val JComponent.bottom: Int
    get() = y + height

@ScaledPixels
internal val JComponent.right: Int
    get() = x + width

@ScaledPixels
internal val JComponent.verticalCenter: Int
    get() = y + height / 2

@ScaledPixels
internal val JComponent.horizontalCenter: Int
    get() = x + width / 2
