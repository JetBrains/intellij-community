// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allowAnalysisFromWriteActionInEdt
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.idea.refactoring.rename.KtReferenceMutateServiceBase
import org.jetbrains.kotlin.idea.references.KtReferenceMutateService
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.generate.UastCommentSaver
import org.jetbrains.uast.generate.UastElementFactory
import org.jetbrains.uast.kotlin.generate.KotlinUastBaseCodeGenerationPlugin
import org.jetbrains.uast.kotlin.generate.KotlinUastElementFactory
import org.jetbrains.uast.kotlin.generate.createUastCommentSaver
import org.jetbrains.uast.toUElementOfType


class IdeaFirKotlinUastCodeGenerationPlugin : KotlinUastBaseCodeGenerationPlugin() {
    override fun shortenReference(sourcePsi: KtElement): PsiElement {
        shortenReferences(sourcePsi)
        return sourcePsi
    }


    private fun KtDotQualifiedExpression.getTargetClassId(): ClassId?= allowAnalysisFromWriteActionInEdt(this) {
        val symbol = receiverExpression.mainReference?.resolveToSymbol() ?: return@allowAnalysisFromWriteActionInEdt null
        return@allowAnalysisFromWriteActionInEdt (symbol as? KaClassSymbol)?.classId
    }

    override fun importMemberOnDemand(reference: UQualifiedReferenceExpression): UExpression? {
        val expression = reference.sourcePsi?.asSafely<KtDotQualifiedExpression>() ?: return null
        val targetClassId = expression.getTargetClassId() ?: return null
        val file = expression.containingKtFile
        file.addImport(targetClassId.asSingleFqName(), allUnder = true)

        return allowAnalysisFromWriteActionInEdt(file) {
            shortenReferences(
                file,
                callableShortenStrategy = { symbol ->
                    val containingClassId = when (symbol) {
                        is KaConstructorSymbol -> symbol.containingClassId
                        else -> symbol.callableId?.classId
                    }
                    if (containingClassId == targetClassId) {
                        ShortenStrategy.SHORTEN_AND_STAR_IMPORT
                    } else {
                        ShortenStrategy.DO_NOT_SHORTEN
                    }
                }
            )
        }?.toUElementOfType()
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun getElementFactory(project: Project): UastElementFactory {
        return object : KotlinUastElementFactory(project) {
            override fun moveLambdaOutsideParenthesis(methodCall: KtCallExpression) {
                val mutateService =
                    ApplicationManager.getApplication().getService(KtReferenceMutateService::class.java) as KtReferenceMutateServiceBase

                allowAnalysisOnEdt {
                    if (mutateService.canMoveLambdaOutsideParentheses(methodCall)) {
                        methodCall.moveFunctionLiteralOutsideParentheses()
                    }
                }
            }
        }
    }

    override fun grabComments(firstResultUElement: UElement, lastResultUElement: UElement): UastCommentSaver? {
        return createUastCommentSaver(firstResultUElement, lastResultUElement)
    }
}