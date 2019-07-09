// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.tensorFlow

import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.impl.PyImportResolver
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import org.jetbrains.annotations.ApiStatus

internal const val KERAS: String = "tensorflow.python.keras.api._v[__VERSION__].keras"
internal const val ESTIMATOR: String = "tensorflow_estimator.python.estimator.api._v[__VERSION__].estimator"
internal const val OTHERS: String = "tensorflow._api.v[__VERSION__]"

internal fun resolveInTensorFlow(qualifiedNameTemplate: String, context: PyQualifiedNameResolveContext): Sequence<List<PsiElement>> {
  return sequenceOf("2", "1")
    .map { qualifiedNameTemplate.replaceFirst("[__VERSION__]", it) }
    .map { QualifiedName.fromDottedString(it) }
    .map { resolveQualifiedName(it, context) }
}

internal fun takeFirstResolvedInTensorFlow(qualifiedNameTemplate: String, context: PyQualifiedNameResolveContext): PsiElement? {
  return resolveInTensorFlow(qualifiedNameTemplate, context).mapNotNull { it.firstOrNull() }.firstOrNull()
}

@ApiStatus.Internal
@ApiStatus.NonExtendable
class PyTensorFlowImportResolver : PyImportResolver {

  override fun resolveImportReference(name: QualifiedName, context: PyQualifiedNameResolveContext, withRoots: Boolean): PsiElement? {
    // resolve `from tensorflow.<reference>` reference
    // tensorflow submodules and subpackages are appended in runtime and have original location in other places

    return when {
      name.matchesPrefix(QualifiedName.fromComponents("tensorflow", "keras")) -> {
        takeFirstResolvedInTensorFlow("$KERAS.${name.removeHead(2)}", context.copyWithoutForeign())
      }
      name.matchesPrefix(QualifiedName.fromComponents("tensorflow", "estimator")) -> {
        takeFirstResolvedInTensorFlow("$ESTIMATOR.${name.removeHead(2)}", context.copyWithoutForeign())
      }
      name.firstComponent == "tensorflow" && name.componentCount >= 2 -> {
        takeFirstResolvedInTensorFlow("$OTHERS.${name.removeHead(1)}", context.copyWithoutForeign())
      }
      else -> null
    }
  }
}