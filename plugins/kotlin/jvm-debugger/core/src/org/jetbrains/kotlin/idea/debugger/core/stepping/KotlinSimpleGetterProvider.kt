// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.core.stepping

import com.intellij.debugger.engine.SimplePropertyGetterProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.psi.singleExpressionBody
import org.jetbrains.kotlin.psi.*

class KotlinSimpleGetterProvider : SimplePropertyGetterProvider {
    override fun isInsideSimpleGetter(element: PsiElement): Boolean {
        // class A(val a: Int)
        if (element is KtParameter) {
            return true
        }

        val accessor = PsiTreeUtil.getParentOfType(element, KtPropertyAccessor::class.java)
        if (accessor != null && accessor.isGetter) {
            return accessor.singleExpressionBody()?.textMatches("field") == true
        }

        val property = PsiTreeUtil.getParentOfType(element, KtProperty::class.java)
        // val a = foo()
        if (property != null) {
            return property.getter == null && !property.isLocal
        }

        return false
    }
}
