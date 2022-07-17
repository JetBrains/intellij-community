// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping

import com.intellij.debugger.engine.SimplePropertyGetterProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.*

class KotlinSimpleGetterProvider : SimplePropertyGetterProvider {
    override fun isInsideSimpleGetter(element: PsiElement): Boolean {
        // class A(val a: Int)
        if (element is KtParameter) {
            return true
        }

        val accessor = PsiTreeUtil.getParentOfType(element, KtPropertyAccessor::class.java)
        if (accessor != null && accessor.isGetter) {
            return when (val body = accessor.bodyExpression) {
                is KtBlockExpression -> {
                    // val a: Int get() { return field }
                    val returnedExpression = (body.statements.singleOrNull() as? KtReturnExpression)?.returnedExpression ?: return false
                    returnedExpression.textMatches("field")
                }
                is KtExpression -> body.textMatches("field") // val a: Int get() = field
                else -> false
            }
        }

        val property = PsiTreeUtil.getParentOfType(element, KtProperty::class.java)
        // val a = foo()
        if (property != null) {
            return property.getter == null && !property.isLocal
        }

        return false
    }
}
