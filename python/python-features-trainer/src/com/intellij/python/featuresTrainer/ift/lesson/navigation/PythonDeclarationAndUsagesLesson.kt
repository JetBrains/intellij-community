// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.featuresTrainer.ift.lesson.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl
import training.dsl.LessonContext
import training.learn.lesson.general.navigation.DeclarationAndUsagesLesson

class PythonDeclarationAndUsagesLesson : DeclarationAndUsagesLesson() {
  override fun LessonContext.setInitialPosition() = caret(9, 21)
  override val sampleFilePath: String = "src/declaration_and_usages_demo.py"
  override val entityName = "discriminant"

  override fun getParentExpression(element: PsiElement): PsiElement? {
    return element.parentOfType<PyReferenceExpressionImpl>(false)
  }
}
