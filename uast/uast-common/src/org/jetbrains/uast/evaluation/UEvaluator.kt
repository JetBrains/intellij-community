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
package org.jetbrains.uast.evaluation

import com.intellij.openapi.extensions.Extensions
import com.intellij.psi.PsiElement
import org.jetbrains.uast.*
import org.jetbrains.uast.values.UDependency
import org.jetbrains.uast.values.UValue

// Role: at the current state, evaluate expression(s)
interface UEvaluator {

  val context: UastContext

  val languageExtensions: List<UEvaluatorExtension>
    get() {
      val rootArea = Extensions.getRootArea()
      if (!rootArea.hasExtensionPoint(UEvaluatorExtension.EXTENSION_POINT_NAME.name)) return listOf()
      return rootArea.getExtensionPoint(UEvaluatorExtension.EXTENSION_POINT_NAME).extensions.toList()
    }

  fun PsiElement.languageExtension(): UEvaluatorExtension? = languageExtensions.firstOrNull { it.language == language }

  fun UElement.languageExtension(): UEvaluatorExtension? = psi?.languageExtension()

  fun analyze(method: UMethod, state: UEvaluationState = method.createEmptyState())

  fun analyze(field: UField, state: UEvaluationState = field.createEmptyState())

  fun evaluate(expression: UExpression, state: UEvaluationState? = null): UValue

  fun getDependents(dependency: UDependency): Set<UValue>
}

fun createEvaluator(context: UastContext, extensions: List<UEvaluatorExtension>): UEvaluator =
  TreeBasedEvaluator(context, extensions)
