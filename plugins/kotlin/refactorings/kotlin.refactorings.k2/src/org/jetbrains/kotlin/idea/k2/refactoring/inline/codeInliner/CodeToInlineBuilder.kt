// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.AbstractCodeToInlineBuilder
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.MutableCodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.ResolvedImportPath
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.ImportPath

open class CodeToInlineBuilder(
    private val original: KtDeclaration, private val imports: List<String> = emptyList(), fallbackToSuperCall: Boolean = false
) : AbstractCodeToInlineBuilder(original.project, original, fallbackToSuperCall) {

    @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class) //called under potemkin progress
    override fun prepareMutableCodeToInline(
        mainExpression: KtExpression?, statementsBefore: List<KtExpression>, reformat: Boolean
    ): MutableCodeToInline {
        allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                val alwaysKeepMainExpression = mainExpression != null && analyze(mainExpression) {
                    val targetSymbol = mainExpression.resolveToCall()?.successfulVariableAccessCall()?.partiallyAppliedSymbol?.symbol
                    when (targetSymbol) {
                        is KaPropertySymbol -> targetSymbol.getter?.isDefault == false
                        else -> false
                    }
                }

                val codeToInline = MutableCodeToInline(
                    mainExpression,
                    original,
                    statementsBefore.toMutableList(),
                    imports.map { ResolvedImportPath(ImportPath(FqName(it), false, null), null) }.toMutableSet(),
                    alwaysKeepMainExpression,
                    extraComments = null,
                )

                saveComments(codeToInline, original)
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