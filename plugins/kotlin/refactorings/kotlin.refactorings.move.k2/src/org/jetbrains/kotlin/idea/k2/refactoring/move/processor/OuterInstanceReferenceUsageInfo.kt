// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.processor

import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

internal sealed class OuterInstanceReferenceUsageInfo(element: PsiElement, private val isIndirectOuter: Boolean) : UsageInfo(element) {
    open fun reportConflictIfAny(conflicts: MultiMap<PsiElement, String>): Boolean {
        val element = element ?: return false
        if (isIndirectOuter) {
            conflicts.putValue(element, KotlinBundle.message("text.indirect.outer.instances.will.not.be.extracted.0", element.text))
            return true
        }
        return false
    }

    class ExplicitThis(
        expression: KtThisExpression,
        isIndirectOuter: Boolean
    ) : OuterInstanceReferenceUsageInfo(expression, isIndirectOuter) {
        val expression: KtThisExpression get() = element as KtThisExpression
    }

    class ImplicitReceiver(
        callElement: KtElement,
        isIndirectOuter: Boolean,
        private val isDoubleReceiver: Boolean
    ) : OuterInstanceReferenceUsageInfo(callElement, isIndirectOuter) {
        val callElement: KtElement get() = element as KtElement

        override fun reportConflictIfAny(conflicts: MultiMap<PsiElement, String>): Boolean {
            if (super.reportConflictIfAny(conflicts)) return true
            val fullCall = callElement.getQualifiedExpressionForSelector() ?: callElement
            return when {
                fullCall is KtQualifiedExpression -> {
                    conflicts.putValue(fullCall, KotlinBundle.message("text.qualified.call.will.not.be.processed.0", fullCall.text))
                    true
                }
                isDoubleReceiver -> {
                    conflicts.putValue(fullCall, KotlinBundle.message("text.member.extension.call.will.not.be.processed.0", fullCall.text))
                    true
                }
                else -> false
            }
        }
    }
}