// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ml.local

import com.intellij.ml.local.models.frequency.classes.ClassesFrequencyModelFactory
import com.intellij.ml.local.models.frequency.classes.ClassesUsagesTracker
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil

class JavaClassesFrequencyModelFactory : ClassesFrequencyModelFactory() {
  override fun fileVisitor(usagesTracker: ClassesUsagesTracker): PsiElementVisitor = object : JavaRecursiveElementWalkingVisitor() {

    override fun visitFile(psiFile: PsiFile) {
      super.visitFile(psiFile)
      usagesTracker.dump()
    }

    override fun visitNewExpression(expression: PsiNewExpression) {
      val cls = expression.classReference?.resolve()
      if (cls is PsiClass) {
        addClassUsage(cls)
      }
      super.visitNewExpression(expression)
    }

    override fun visitClassObjectAccessExpression(expression: PsiClassObjectAccessExpression) {
      PsiTypesUtil.getPsiClass(expression.operand.type)?.let { cls ->
        addClassUsage(cls)
      }
    }

    override fun visitTypeCastExpression(expression: PsiTypeCastExpression) {
      val castType = expression.castType
      if (castType != null) {
        PsiTypesUtil.getPsiClass(castType.type)?.let { cls ->
          addClassUsage(cls)
        }
      }
      expression.operand?.accept(this)
    }

    override fun visitInstanceOfExpression(expression: PsiInstanceOfExpression) {
      expression.operand.accept(this)
      val checkType = expression.checkType ?: return
      PsiTypesUtil.getPsiClass(checkType.type)?.let { cls ->
        addClassUsage(cls)
      }
    }

    override fun visitReferenceExpression(expression: PsiReferenceExpression) {
      expression.qualifierExpression?.let { qualifier ->
        if (qualifier is PsiReferenceExpression) {
          qualifier.resolve()?.let { def ->
            if (def is PsiClass) {
              addClassUsage(def)
            }
          }
        }
      }
      super.visitReferenceExpression(expression)
    }

    private fun addClassUsage(cls: PsiClass) {
      JavaLocalModelsUtil.getClassName(cls)?.let {
        usagesTracker.classUsed(it)
      }
    }

    override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) = Unit
    override fun visitImportStatement(statement: PsiImportStatement) = Unit
    override fun visitImportStaticStatement(statement: PsiImportStaticStatement) = Unit
  }
}