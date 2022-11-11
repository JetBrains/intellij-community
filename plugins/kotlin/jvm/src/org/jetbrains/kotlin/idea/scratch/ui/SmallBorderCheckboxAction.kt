// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch.ui

import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

abstract class SmallBorderCheckboxAction(@NlsContexts.Checkbox text: String, @NlsContexts.Tooltip description: String? = null) :
    CheckboxAction(text, description, null) {
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val checkbox = super.createCustomComponent(presentation, place)
        checkbox.border = JBUI.Borders.emptyRight(4)
        return checkbox
    }
}