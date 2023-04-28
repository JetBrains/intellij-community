// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember

sealed interface KotlinMoveUsage {
    val isInternal: Boolean

    fun refresh(refExpr: KtSimpleNameExpression, referencedElement: PsiElement): UsageInfo

    sealed interface Deferred : KotlinMoveUsage {
        fun resolve(newElement: PsiElement): UsageInfo?

        class CallableReference(
            element: PsiElement,
            reference: PsiReference,
            referencedElement: PsiElement,
            val originalFile: PsiFile,
            private val addImportToOriginalFile: Boolean,
            override val isInternal: Boolean
        ) : Deferred, MoveRenameUsageInfo(
            element,
            reference,
            reference.rangeInElement.startOffset,
            reference.rangeInElement.endOffset,
            referencedElement,
            false
        ) {
            override fun refresh(refExpr: KtSimpleNameExpression, referencedElement: PsiElement): UsageInfo {
                return CallableReference(
                    refExpr,
                    refExpr.mainReference,
                    referencedElement,
                    originalFile,
                    addImportToOriginalFile,
                    isInternal
                )
            }

            override fun resolve(newElement: PsiElement): UsageInfo? {
                val target = newElement.unwrapped
                val element = element ?: return null
                val reference = reference ?: return null
                val referencedElement = referencedElement ?: return null
                if (target != null && target.isTopLevelKtOrJavaMember()) {
                    element.getStrictParentOfType<KtCallableReferenceExpression>()?.receiverExpression?.delete()
                    return Unqualifiable(
                        element,
                        reference,
                        referencedElement,
                        element.containingFile!!,
                        addImportToOriginalFile,
                        isInternal
                    )
                }
                return Qualifiable(element, reference, referencedElement, isInternal)
            }
        }
    }

    class Unqualifiable(
        element: PsiElement,
        reference: PsiReference,
        referencedElement: PsiElement,
        val originalFile: PsiFile,
        val addImportToOriginalFile: Boolean,
        override val isInternal: Boolean
    ) : KotlinMoveUsage, MoveRenameUsageInfo(
        element,
        reference,
        reference.rangeInElement.startOffset,
        reference.rangeInElement.endOffset,
        referencedElement,
        false
    ) {
        override fun refresh(refExpr: KtSimpleNameExpression, referencedElement: PsiElement): UsageInfo {
            return Unqualifiable(
                refExpr,
                refExpr.mainReference,
                referencedElement,
                originalFile,
                addImportToOriginalFile,
                isInternal
            )
        }
    }

    class Qualifiable(
        element: PsiElement,
        reference: PsiReference,
        referencedElement: PsiElement,
        override val isInternal: Boolean
    ) : KotlinMoveUsage, MoveRenameUsageInfo(
        element,
        reference,
        reference.rangeInElement.startOffset,
        reference.rangeInElement.endOffset,
        referencedElement,
        false
    ) {
        override fun refresh(refExpr: KtSimpleNameExpression, referencedElement: PsiElement): UsageInfo {
            return Qualifiable(refExpr, refExpr.mainReference, referencedElement, isInternal)
        }
    }
}

