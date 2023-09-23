// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableService
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.KotlinIntroduceVariableHandler.getCandidateContainers

internal class KotlinIntroduceVariableServiceK2Impl(private val project: Project) : KotlinIntroduceVariableService {
    override fun findElementAtRange(
        file: KtFile,
        selectionStart: Int,
        selectionEnd: Int,
        elementKinds: Collection<ElementKind>
    ): PsiElement? = findElementAtRange(
        file = file,
        selectionStart = selectionStart,
        selectionEnd = selectionEnd,
        elementKinds = elementKinds,
        failOnEmptySuggestion = false,
    )

    override fun getContainersForExpression(expression: KtExpression): List<Pair<KtElement, KtElement>> {
        return expression.getCandidateContainers().map { expression to it }
    }

    override fun getSmartSelectSuggestions(
        file: PsiFile,
        offset: Int,
        elementKind: ElementKind
    ): List<KtElement> {
        return org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.getSmartSelectSuggestions(file, offset, elementKind)
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
}