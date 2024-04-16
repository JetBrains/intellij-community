// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.refactoring.introduce.ExtractableSubstringInfo
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateEntry

class K2ExtractableSubstringInfo(
    startEntry: KtStringTemplateEntry,
    endEntry: KtStringTemplateEntry,
    prefix: String,
    suffix: String,
    isStr: Boolean? = null
) : ExtractableSubstringInfo(startEntry, endEntry, prefix, suffix) {

    context(KtAnalysisSession)
    fun guessLiteralType(): KtType {
        val stringType = builtinTypes.STRING

        if (startEntry != endEntry || startEntry !is KtLiteralStringTemplateEntry) return stringType

        val factory = KtPsiFactory(startEntry.project)

        if (factory.createExpressionIfPossible(content) == null) {
            return stringType
        }

        val expr = factory.createExpressionCodeFragment(content, startEntry).getContentElement() ?: return stringType

        val value = expr.evaluate(KtConstantEvaluationMode.CONSTANT_EXPRESSION_EVALUATION)

        if (value == null) return stringType

        return expr.getKtType() ?: stringType
    }

    override val isString: Boolean = isStr ?: analyze(startEntry) { guessLiteralType().isString }

    override fun copy(
        newStartEntry: KtStringTemplateEntry,
        newEndEntry: KtStringTemplateEntry
    ): ExtractableSubstringInfo = K2ExtractableSubstringInfo(newStartEntry, newEndEntry, prefix, suffix, isString)
}