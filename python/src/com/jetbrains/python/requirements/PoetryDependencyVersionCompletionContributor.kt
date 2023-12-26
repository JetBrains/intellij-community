// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.python.community.impl.requirements.completeVersions
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable

class PoetryDependencyVersionCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val fileName = parameters.originalFile.name
    if (fileName != "pyproject.toml") return
    val project = parameters.editor.project ?: return

    val position = parameters.position
    val parent = position.parent

    val tableName = PsiTreeUtil.getParentOfType(parameters.position, TomlTable::class.java)?.header?.key?.text ?: return
    if (tableName.contains("tool.poetry") && (tableName.contains("dependencies")
                                              || tableName.contains("source"))) {
      if (parent is TomlLiteral || parent is TomlKeyValue) {
        val name = (if (parent is TomlKeyValue) parent else PsiTreeUtil.getParentOfType(parent, TomlKeyValue::class.java))?.key?.text ?: return
        completeVersions(name, project, result)
      }
    }
  }
}