@file:Suppress("MagicNumber") // Swing dimension constants
package com.jetbrains.packagesearch.intellij.plugin.ui.util

import com.intellij.ui.DocumentAdapter
import net.miginfocom.layout.CC
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JTextField
import javax.swing.UIManager
import javax.swing.event.DocumentEvent

@ScaledPixels
internal fun scrollbarWidth() = UIManager.get("ScrollBar.width") as Int

internal fun CC.compensateForHighlightableComponentMarginLeft() = pad(0, (-2).scaled(), 0, 0)

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

internal fun JTextField.addOnTextChangedListener(onTextChanged: (DocumentEvent) -> Unit) {
    document.addDocumentListener(
        object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                onTextChanged(e)
            }
        }
    )
}
