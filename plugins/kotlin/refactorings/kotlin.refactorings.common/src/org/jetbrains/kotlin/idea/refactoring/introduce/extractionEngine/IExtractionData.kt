// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.refactoring.introduce.ExtractableSubstringInfo
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile

interface IExtractionData : Disposable {
    val originalFile: KtFile
    val originalRange: KotlinPsiRange
    val targetSibling: PsiElement
    val duplicateContainer: PsiElement?
    val options: ExtractionOptions
    val expressions: List<KtExpression>
    val codeFragmentText: String
    val project: Project
    val insertBefore: Boolean
    val originalElements: List<PsiElement>
    val physicalElements: List<PsiElement>
    val substringInfo: ExtractableSubstringInfo?
    val commonParent: KtElement
}