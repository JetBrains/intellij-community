// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.python.pyproject.psi.isPyProjectToml
import com.jetbrains.python.requirements.completeVersions
import com.jetbrains.python.requirements.getPythonSdk
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral

class PoetryDependencyVersionCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (!parameters.originalFile.isPyProjectToml()) return
    val poetryTomlTable = parameters.position.getPoetryTomlTable() ?: return
    if (!poetryTomlTable.header.endsWith("dependencies")) return

    val (packageName, addQuotes) = when (val parent = parameters.position.parent) {
      is TomlKeyValue -> parent.key.text to true
      is TomlLiteral -> PsiTreeUtil.getParentOfType(parent, TomlKeyValue::class.java)?.key?.text to false
      else -> return
    }

    val sdk = getPythonSdk(parameters.originalFile) ?: return
    completeVersions(packageName, parameters.position.project, sdk, result, addQuotes)
  }
}