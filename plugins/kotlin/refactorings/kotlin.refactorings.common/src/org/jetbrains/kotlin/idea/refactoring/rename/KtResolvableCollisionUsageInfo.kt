// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.ResolvableCollisionUsageInfo
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

abstract class KtResolvableCollisionUsageInfo(
    element: PsiElement,
    referencedElement: PsiElement
) : ResolvableCollisionUsageInfo(element, referencedElement) {
    // To prevent simple rename via PsiReference
    override fun getReference() = null

    abstract fun apply()
}

class UsageInfoWithReplacement(
    element: PsiElement,
    referencedElement: PsiElement,
    private val replacement: KtElement
) : KtResolvableCollisionUsageInfo(element, referencedElement) {
    override fun apply() {
        val toBeReplaced = (element?.parent as? KtCallableReferenceExpression)?.takeIf {
            // ::element -> this::newElement
            replacement is KtCallableReferenceExpression
        } ?: element
        toBeReplaced?.replaced(replacement)?.let {
            KotlinRenameRefactoringSupport.getInstance().shortenReferencesLater(it)
        }
    }
}

class UsageInfoWithFqNameReplacement(
    element: KtSimpleNameExpression,
    referencedElement: PsiElement,
    private val newFqName: FqName
) : KtResolvableCollisionUsageInfo(element, referencedElement) {
    override fun apply() {
        (element as? KtSimpleNameExpression)?.mainReference?.bindToFqName(newFqName)
    }
}