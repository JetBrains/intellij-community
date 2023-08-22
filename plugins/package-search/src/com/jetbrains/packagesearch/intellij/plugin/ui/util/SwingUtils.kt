/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

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

internal fun CC.compensateForHighlightableComponentMarginLeft() = pad("0 -2 0 0")

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

fun JTextField.addOnTextChangedListener(onTextChanged: (DocumentEvent) -> Unit) {
    document.addDocumentListener(
        object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                onTextChanged(e)
            }
        }
    )
}
