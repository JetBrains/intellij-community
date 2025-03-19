// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.refactoring.introduce.ExtractableSubstringInfo
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateEntry

/**
 * If `isStr` is not provided, analysis session is started in init to determine the type of the expression.
 */
class K2ExtractableSubstringInfo(
    startEntry: KtStringTemplateEntry,
    endEntry: KtStringTemplateEntry,
    prefix: String,
    suffix: String,
    isStr: Boolean? = null
) : ExtractableSubstringInfo(startEntry, endEntry, prefix, suffix) {

    context(KaSession)
    fun guessLiteralType(): KaType {
        val stringType = builtinTypes.string

        if (startEntry != endEntry || startEntry !is KtLiteralStringTemplateEntry) return stringType

        val factory = KtPsiFactory(startEntry.project)

        if (factory.createExpressionIfPossible(content) == null) {
            return stringType
        }

        val expr = factory.createExpressionCodeFragment(content, startEntry).getContentElement() ?: return stringType

        val value = expr.evaluate()

        if (value == null) return stringType

        return expr.expressionType ?: stringType
    }

    override val isString: Boolean = isStr ?: analyze(startEntry) { guessLiteralType().isStringType }

    override fun copy(
        newStartEntry: KtStringTemplateEntry,
        newEndEntry: KtStringTemplateEntry
    ): ExtractableSubstringInfo = K2ExtractableSubstringInfo(newStartEntry, newEndEntry, prefix, suffix, isString)
}