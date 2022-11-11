// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard.ui

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.SwingConstants

class CommentLabel(@NlsContexts.Label text: String? = null) : JBLabel() {
    init {
        if (text != null) {
            this.text = text
            setCopyable(true) // hyperlinks support
        }
        verticalAlignment = SwingConstants.TOP
        isFocusable = false
        foreground = UIUtil.getContextHelpForeground()

        // taken from com.intellij.openapi.ui.panel.ComponentPanelBuilder.createCommentComponent
        if (SystemInfo.isMac) {
            val font = this.font
            val size = font.size2D
            val smallFont = font.deriveFont(size - 2.0f)
            this.font = smallFont
        }
    }
}

fun commentLabel(@NlsContexts.Label text: String, init: JBLabel.() -> Unit = {}) =
    CommentLabel(text).apply(init)

fun componentWithCommentAtBottom(component: JComponent, label: String?, gap: Int = 4) = borderPanel {
    addToTop(component)
    label?.let {
        addToCenter(commentLabel(it) {
            withBorder(JBUI.Borders.emptyLeft(gap))
        })
    }
}