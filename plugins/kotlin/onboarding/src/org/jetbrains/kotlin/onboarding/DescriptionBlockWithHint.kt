// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding

import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.feedback.dialog.uiBlocks.DescriptionBlock
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Panel

class DescriptionBlockWithHint(
    @NlsContexts.Label private val myLabel: String,
    @NlsContexts.Label private val myHint: String
) : DescriptionBlock(myLabel) {

    override fun addToPanel(panel: Panel) {
        panel.apply {
            row {
                text(myLabel, maxLineLength = MAX_LINE_LENGTH_WORD_WRAP).validationRequestor { validate -> validate() }
                rowComment(myHint)
            }.bottomGap(BottomGap.SMALL)
        }
    }
}