package com.intellij.debugger.streams.psi.impl

import com.intellij.debugger.streams.psi.PsiElementTransformer
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.refactoring.util.LambdaRefactoringUtil

/**
 * @author Vitaliy.Bibaev
 */
object MethodReferenceToLambdaTransformer : PsiElementTransformer.Base() {
  override val visitor: PsiElementVisitor
    get() = object : JavaRecursiveElementVisitor() {
      override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression?) {
        super.visitMethodReferenceExpression(expression)
        if (expression == null) return
        LambdaRefactoringUtil.convertMethodReferenceToLambda(expression, false, true)
      }
    }
}