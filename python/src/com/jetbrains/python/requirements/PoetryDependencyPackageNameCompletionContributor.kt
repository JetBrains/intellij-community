// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.psi.util.PsiTreeUtil
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlTable

class PoetryDependencyPackageNameCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.originalFile.name != "pyproject.toml") return
    val project = parameters.editor.project ?: return

    val position = parameters.position
    val parent = position.parent

    val tableName = PsiTreeUtil.getParentOfType(parameters.position, TomlTable::class.java)?.header?.key?.text ?: return
    if (tableName.contains("tool.poetry") && (tableName.contains("dependencies")
                                              || tableName.contains("source"))) {
      if (parent is TomlKeySegment) {
        completePackageNames(project, result)
      }
    }
  }
}