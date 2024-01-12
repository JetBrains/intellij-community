// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pyproject

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.pip.PypiPackageCache
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import org.toml.lang.psi.TOML_STRING_LITERALS
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.ext.name

class PyprojectPackageCompletionContributor : CompletionContributor() {

  private val completionLocations = listOf("dependencies", "requires")
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.originalFile.name != "pyproject.toml") return
    val position = parameters.position
    val parent = position.parent

    val key = parent.parentOfType<TomlKeyValue>()?.key?.name
    if (TOML_STRING_LITERALS.contains(position.elementType)
        && parent.parent is TomlArray
        && key in completionLocations) {
      val cache = service<PypiPackageCache>()
      val maxPriority = cache.packages.size
      cache.packages.asSequence()
        .map {
          LookupElementBuilder.create(it.lowercase()).withTypeText(PyBundle.message("python.pyproject.package.completion.tail")).withIcon(
            PythonPsiApiIcons.Python)
        }
        .mapIndexed { index, lookupElementBuilder ->
          PrioritizedLookupElement.withPriority(lookupElementBuilder, (maxPriority - index).toDouble())
        }
        .forEach { result.addElement(it) }
    }
  }
}