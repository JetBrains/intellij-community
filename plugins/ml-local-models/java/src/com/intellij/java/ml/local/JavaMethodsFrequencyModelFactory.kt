// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ml.local

import com.intellij.ml.local.models.frequency.methods.MethodsFrequencyModelFactory
import com.intellij.ml.local.models.frequency.methods.MethodsUsagesTracker
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*

class JavaMethodsFrequencyModelFactory : MethodsFrequencyModelFactory() {
  override fun fileVisitor(usagesTracker: MethodsUsagesTracker): PsiElementVisitor = object : JavaRecursiveElementWalkingVisitor() {

    override fun visitFile(psiFile: PsiFile) {
      super.visitFile(psiFile)
      usagesTracker.dump()
    }

    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
      ProgressManager.checkCanceled()
      expression.resolveMethod()?.let { method ->
        JavaLocalModelsUtil.getMethodName(method)?.let { methodName ->
          method.containingClass?.let { cls ->
            JavaLocalModelsUtil.getClassName(cls)?.let { clsName ->
              usagesTracker.methodUsed(clsName, methodName)
            }
          }
        }
      }
      super.visitMethodCallExpression(expression)
    }

    override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) = Unit
    override fun visitImportStatement(statement: PsiImportStatement) = Unit
    override fun visitImportStaticStatement(statement: PsiImportStaticStatement) = Unit
  }
}