// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile

@Internal
interface KotlinIntroduceVariableService {
    fun findElement(
        file: KtFile,
        startOffset: Int,
        endOffset: Int,
        failOnNoExpression: Boolean,
        elementKind: ElementKind
    ): PsiElement?

    fun getContainersForExpression(expression: KtExpression): List<KotlinIntroduceVariableHelper.Containers>
    fun findOccurrences(expression: KtExpression, occurrenceContainer: PsiElement): List<KtExpression>

    fun doRefactoringWithContainer(
        editor: Editor?,
        expressionToExtract: KtExpression,
        container: KtElement,
        occurrencesToReplace: List<KtExpression>?,
    )

    fun hasUnitType(element: KtExpression): Boolean
}