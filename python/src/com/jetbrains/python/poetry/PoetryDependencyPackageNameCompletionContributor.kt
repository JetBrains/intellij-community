// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.python.pyproject.psi.isPyProjectToml
import com.jetbrains.python.requirements.completePackageNames
import com.jetbrains.python.requirements.getPythonSdk
import org.toml.lang.psi.TomlKeySegment

class PoetryDependencyPackageNameCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (!parameters.originalFile.isPyProjectToml()) return
    val poetryTomlTable = parameters.position.getPoetryTomlTable() ?: return
    if (!poetryTomlTable.header.endsWith("dependencies")) return

    if (parameters.position.parent is TomlKeySegment) {
      val sdk = getPythonSdk(parameters.originalFile) ?: return
      completePackageNames(parameters.position.project, sdk, result)
    }
  }
}