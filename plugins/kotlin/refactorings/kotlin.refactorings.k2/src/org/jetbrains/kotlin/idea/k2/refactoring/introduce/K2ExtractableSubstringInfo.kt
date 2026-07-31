// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.evaluation.evaluate
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.builtinTypes
import org.jetbrains.kotlin.analysis.api.types.isStringType
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

    context(_: KaSession)
    fun guessLiteralType(): KaType {
        val stringType = builtinTypes.string

        if (startEntry != endEntry || startEntry !is KtLiteralStringTemplateEntry) return stringType

        val factory = KtPsiFactory(startEntry.project)

        if (factory.createExpressionIfPossible(content) == null) {
            return stringType
        }

        val expr = factory.createExpressionCodeFragment(content, startEntry).getContentElement() ?: return stringType

        val selectedConstantId = analyze(expr) {
            (expr.takeIf { expr.evaluate() != null }?.expressionType as? KaClassType)?.classId
        }

        return when (selectedConstantId) {
            KaStandardTypeClassIds.INT -> builtinTypes.int
            KaStandardTypeClassIds.BOOLEAN -> builtinTypes.boolean
            KaStandardTypeClassIds.BYTE -> builtinTypes.byte
            KaStandardTypeClassIds.CHAR -> builtinTypes.char
            KaStandardTypeClassIds.SHORT -> builtinTypes.short
            KaStandardTypeClassIds.LONG -> builtinTypes.long
            KaStandardTypeClassIds.FLOAT -> builtinTypes.float
            KaStandardTypeClassIds.DOUBLE -> builtinTypes.double
            else -> stringType
        }
    }

    override val isString: Boolean = isStr ?: analyze(startEntry) { guessLiteralType().isStringType }

    override fun copy(
        newStartEntry: KtStringTemplateEntry,
        newEndEntry: KtStringTemplateEntry
    ): ExtractableSubstringInfo = K2ExtractableSubstringInfo(newStartEntry, newEndEntry, prefix, suffix, isString)
}
