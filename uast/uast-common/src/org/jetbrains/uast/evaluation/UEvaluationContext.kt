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

import com.intellij.openapi.util.Key
import org.jetbrains.uast.*
import org.jetbrains.uast.values.UValue
import java.lang.ref.SoftReference

interface UEvaluationContext {
  val uastContext: UastContext

  val extensions: List<UEvaluatorExtension>

  fun analyzeAll(file: UFile, state: UEvaluationState = file.createEmptyState()): UEvaluationContext

  fun analyze(declaration: UDeclaration, state: UEvaluationState = declaration.createEmptyState()): UEvaluator

  fun valueOf(expression: UExpression): UValue

  fun valueOfIfAny(expression: UExpression): UValue?

  fun getEvaluator(declaration: UDeclaration): UEvaluator
}

fun UFile.analyzeAll(context: UastContext = getUastContext(), extensions: List<UEvaluatorExtension> = emptyList()): UEvaluationContext =
  MapBasedEvaluationContext(context, extensions).analyzeAll(this)

@JvmOverloads
fun UExpression?.uValueOf(extensions: List<UEvaluatorExtension> = emptyList()): UValue? {
  if (this == null) return null
  val declaration = getContainingAnalyzableDeclaration() ?: return null
  val context = declaration.getEvaluationContextWithCaching(extensions)
  context.analyze(declaration)
  return context.valueOf(this)
}

fun UExpression?.uValueOf(vararg extensions: UEvaluatorExtension): UValue? = uValueOf(extensions.asList())

private fun UElement.getContainingAnalyzableDeclaration() = withContainingElements.filterIsInstance<UDeclaration>().firstOrNull {
  it is UMethod ||
  it is UField // TODO: think about field analysis (should we use class as analyzable declaration)
}

fun UDeclaration.getEvaluationContextWithCaching(extensions: List<UEvaluatorExtension> = emptyList()): UEvaluationContext {
  return containingFile?.let { file ->
    val cachedContext = file.getUserData(EVALUATION_CONTEXT_KEY)?.get()
    if (cachedContext != null && cachedContext.extensions == extensions)
      cachedContext
    else
      MapBasedEvaluationContext(getUastContext(), extensions).apply {
        file.putUserData(EVALUATION_CONTEXT_KEY, SoftReference(this))
      }

  } ?: MapBasedEvaluationContext(getUastContext(), extensions)
}

val EVALUATION_CONTEXT_KEY: Key<SoftReference<out UEvaluationContext>> = Key<SoftReference<out UEvaluationContext>>("uast.EvaluationContext")
