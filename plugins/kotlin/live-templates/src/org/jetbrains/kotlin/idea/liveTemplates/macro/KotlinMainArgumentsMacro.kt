// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.liveTemplates.macro

import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings

class KotlinMainArgumentsMacro : KotlinMacro() {
    override fun getName() = "kotlinMainArguments"
    override fun getPresentableName() = "kotlinMainArguments()"

    override fun calculateResult(params: Array<Expression>, context: ExpressionContext): Result? {
        val languageVersionSettings = context.psiElementAtStartOffset?.languageVersionSettings ?: return null
        if (languageVersionSettings.supportsFeature(LanguageFeature.ExtendedMainConvention)) return TextResult("")
        return TextResult("args: Array<String>")
    }
}