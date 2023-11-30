// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.analysis.api.utils.analyzeInModalWindow
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.KotlinIntroduceVariableHandler.getCandidateContainers
import org.jetbrains.kotlin.idea.refactoring.introduce.IntroduceRefactoringException
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableService
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.findElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile

internal class KotlinIntroduceVariableServiceK2Impl(private val project: Project) : KotlinIntroduceVariableService {
    override fun findElement(
        file: KtFile,
        startOffset: Int,
        endOffset: Int,
        failOnNoExpression: Boolean,
        elementKind: ElementKind
    ): PsiElement? {
        val element = findElement(file, startOffset, endOffset, elementKind)

        if (element == null) {
            if (failOnNoExpression) {
                throw IntroduceRefactoringException(KotlinBundle.message("cannot.refactor.not.expression"))
            }
            return null
        }

        return element
    }

    override fun getContainersForExpression(expression: KtExpression): List<Pair<KtElement, KtElement>> {
        return expression.getCandidateContainers().map { expression to it }
    }

    override fun findOccurrences(
        expression: KtExpression,
        occurrenceContainer: PsiElement
    ): List<KtExpression> = listOf(expression)

    override fun doRefactoringWithContainer(
        editor: Editor?,
        expressionToExtract: KtExpression,
        container: KtElement,
        occurrencesToReplace: List<KtExpression>?
    ) {
        KotlinIntroduceVariableHandler.doRefactoring(
            project, editor,
            expressionToExtract,
            container,
            isVar = false,
        )
    }

    override fun hasUnitType(element: KtExpression): Boolean {
        return analyzeInModalWindow(element, KotlinBundle.message("find.usages.prepare.dialog.progress")) {
            val expressionType = element.getKtType()
            expressionType == null || expressionType.isUnit
        }
    }
}