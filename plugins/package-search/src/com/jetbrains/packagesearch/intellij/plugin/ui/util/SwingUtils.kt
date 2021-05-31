@file:Suppress("MagicNumber") // Swing dimension constants
package com.jetbrains.packagesearch.intellij.plugin.ui.util

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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


@Suppress("unused") // The receiver is technically unused
internal val Dispatchers.AppUI
    get() = AppUIExecutor.onUiThread().coroutineDispatchingContext()
