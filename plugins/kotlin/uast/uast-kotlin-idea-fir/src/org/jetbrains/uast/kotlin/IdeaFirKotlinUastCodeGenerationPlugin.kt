// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.idea.refactoring.rename.KtReferenceMutateServiceBase
import org.jetbrains.kotlin.idea.references.KtReferenceMutateService
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.UElement
import org.jetbrains.uast.generate.UastCommentSaver
import org.jetbrains.uast.generate.UastElementFactory
import org.jetbrains.uast.kotlin.generate.KotlinUastBaseCodeGenerationPlugin
import org.jetbrains.uast.kotlin.generate.KotlinUastElementFactory
import org.jetbrains.uast.kotlin.generate.createUastCommentSaver


class IdeaFirKotlinUastCodeGenerationPlugin : KotlinUastBaseCodeGenerationPlugin(){
    override fun shortenReference(sourcePsi: KtElement): PsiElement {
        shortenReferences(sourcePsi)
        return sourcePsi
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