// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pyproject

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import org.toml.lang.psi.TOML_STRING_LITERALS
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.ext.name

class PyprojectMetadataCompletionContributor : CompletionContributor() {


  private val knownBackends = listOf("setuptools.build_meta",
                                     "setuptools.build_meta:__legacy__",
                                     "poetry.core.masonry.api",
                                     "flit_core.buildapi",
                                     "pdm.backend",
                                     "hatchling.build")

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.originalFile.name != "pyproject.toml") return
    val position = parameters.position
    val parent = position.parent

    if (TOML_STRING_LITERALS.contains(parent.elementType)  && parent.parentOfType<TomlKeyValue>()?.key?.name == "build-backend") {
      knownBackends.map {
        LookupElementBuilder.create(it).withIcon(PythonPsiApiIcons.Python)
      }
      .forEach { result.addElement(it) }
    }
  }


}