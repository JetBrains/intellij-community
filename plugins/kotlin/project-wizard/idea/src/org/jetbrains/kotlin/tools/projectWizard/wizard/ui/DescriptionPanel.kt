// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard.ui

import com.intellij.util.ui.HtmlPanel
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Font

class DescriptionPanel(@Nls initialText: String? = null) : HtmlPanel() {
    @Nls
    private var bodyText: String? = initialText

    fun updateText(@Nls text: String) {
        bodyText = text.asHtml()
        update()
    }

    override fun getBody() = bodyText.orEmpty()

    override fun getBodyFont(): Font = UIUtil.getButtonFont().deriveFont(Font.PLAIN)
}