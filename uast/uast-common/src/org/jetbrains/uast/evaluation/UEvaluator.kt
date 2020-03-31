// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.evaluation

import com.intellij.psi.PsiElement
import org.jetbrains.uast.*
import org.jetbrains.uast.values.UDependency
import org.jetbrains.uast.values.UValue

// Role: at the current state, evaluate expression(s)
interface UEvaluator {
  val context: UastLanguagePlugin

  val languageExtensions: List<UEvaluatorExtension>
    get() = UEvaluatorExtension.EXTENSION_POINT_NAME.extensionsIfPointIsRegistered

  fun PsiElement.languageExtension(): UEvaluatorExtension? = languageExtensions.firstOrNull { it.language == language }

  fun UElement.languageExtension(): UEvaluatorExtension? = sourcePsi?.languageExtension()

  fun analyze(method: UMethod, state: UEvaluationState = method.createEmptyState())

  fun analyze(field: UField, state: UEvaluationState = field.createEmptyState())

  fun evaluate(expression: UExpression, state: UEvaluationState? = null): UValue

  fun evaluateVariableByReference(variableReference: UReferenceExpression, state: UEvaluationState? = null): UValue

  fun getDependents(dependency: UDependency): Set<UValue>
}

fun createEvaluator(context: UastLanguagePlugin, extensions: List<UEvaluatorExtension>): UEvaluator =
  TreeBasedEvaluator(context, extensions)
