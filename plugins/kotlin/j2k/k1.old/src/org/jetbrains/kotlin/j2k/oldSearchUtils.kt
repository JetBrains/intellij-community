// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.K1Deprecation

@K1Deprecation
fun ReferenceSearcher.findMethodCalls(method: PsiMethod, scope: PsiElement): Collection<PsiMethodCallExpression> {
    return findLocalUsages(method, scope).mapNotNull {
        if (it is PsiReferenceExpression) {
            val methodCall = it.parent as? PsiMethodCallExpression
            if (methodCall?.methodExpression == it) methodCall else null
        }
        else {
            null
        }
    }
}

@K1Deprecation
fun PsiField.isVar(searcher: ReferenceSearcher): Boolean {
    if (hasModifierProperty(PsiModifier.FINAL)) return false
    if (!hasModifierProperty(PsiModifier.PRIVATE)) return true
    val containingClass = containingClass ?: return true
    val writes = searcher.findVariableUsages(this, containingClass).filter { PsiUtil.isAccessedForWriting(it) }
    if (writes.size == 0) return false
    if (writes.size > 1) return true
    val write = writes.single()
    val parent = write.parent
    if (parent is PsiAssignmentExpression
        && parent.operationSign.tokenType == JavaTokenType.EQ
        && write.isQualifierEmptyOrThis()
    ) {
        val constructor = write.getContainingConstructor()
        return constructor == null
               || constructor.containingClass != containingClass
               || !(parent.parent is PsiExpressionStatement)
               || parent.parent?.parent != constructor.body
    }
    return true
}

@K1Deprecation
fun PsiVariable.isInVariableInitializer(searcher: ReferenceSearcher, scope: PsiElement?): Boolean {
    return if (scope != null) searcher.findVariableUsages(this, scope).any {
        val parent = PsiTreeUtil.skipParentsOfType(it, PsiParenthesizedExpression::class.java)
        parent is PsiVariable && parent.initializer == it
    } else false
}
