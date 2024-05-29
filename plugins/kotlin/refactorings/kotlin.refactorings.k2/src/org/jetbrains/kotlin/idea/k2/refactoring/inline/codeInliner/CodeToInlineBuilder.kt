// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import org.jetbrains.kotlin.analysis.api.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.AbstractCodeToInlineBuilder
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.MutableCodeToInline
import org.jetbrains.kotlin.psi.*

class CodeToInlineBuilder(
    private val original: KtDeclaration, fallbackToSuperCall: Boolean = false
) : AbstractCodeToInlineBuilder(original.project, original, fallbackToSuperCall) {

    @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class) //called under potemkin progress
    override fun prepareMutableCodeToInline(
        mainExpression: KtExpression?, statementsBefore: List<KtExpression>, reformat: Boolean
    ): MutableCodeToInline {
        allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                val codeToInline = super.prepareMutableCodeToInline(mainExpression, statementsBefore, reformat)
                insertExplicitTypeArguments(codeToInline)
                removeContracts(codeToInline)
                encodeInternalReferences(codeToInline, original)
                specifyFunctionLiteralTypesExplicitly(codeToInline)
                specifyNullTypeExplicitly(codeToInline, original)
                return codeToInline
            }
        }
    }
}