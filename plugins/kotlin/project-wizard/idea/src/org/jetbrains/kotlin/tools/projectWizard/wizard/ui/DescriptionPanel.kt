// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard.ui

import com.intellij.util.ui.HtmlPanel
import com.intellij.util.ui.UIUtil
import java.awt.Font

class DescriptionPanel(initialText: String? = null) : HtmlPanel() {
    private var bodyText: String? = initialText

    fun updateText(text: String) {
        bodyText = text.asHtml()
        update()
    }

    override fun getBody() = bodyText.orEmpty()

    override fun getBodyFont(): Font = UIUtil.getButtonFont().deriveFont(Font.PLAIN)
}