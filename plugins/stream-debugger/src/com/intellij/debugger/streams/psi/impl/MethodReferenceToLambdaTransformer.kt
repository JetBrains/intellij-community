/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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