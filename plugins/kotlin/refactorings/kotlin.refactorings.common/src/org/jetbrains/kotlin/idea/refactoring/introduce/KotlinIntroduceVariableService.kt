// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile

@Internal
interface KotlinIntroduceVariableService {
    fun findElementAtRange(
        file: KtFile,
        selectionStart: Int,
        selectionEnd: Int,
        elementKinds: Collection<ElementKind>,
    ): PsiElement?

    fun getContainersForExpression(expression: KtExpression): List<Pair<KtElement, KtElement>>
    fun getSmartSelectSuggestions(file: PsiFile, offset: Int, elementKind: ElementKind): List<KtElement>
    fun findOccurrences(expression: KtExpression, occurrenceContainer: PsiElement): List<KtExpression>

    fun doRefactoringWithContainer(
        editor: Editor?,
        expressionToExtract: KtExpression,
        container: KtElement,
        occurrencesToReplace: List<KtExpression>?,
    )
}