// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.requirements.psi.NameReq
import com.jetbrains.python.requirements.psi.VersionStmt

class RequirementsVersionCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val position = parameters.position
    val parent = position.parent
    val project = parameters.editor.project ?: return

    if (parent is VersionStmt) {
      val name = PsiTreeUtil.getParentOfType(parent, NameReq::class.java)?.simpleName?.text ?: return
      completeVersions(name, project, result, false)
    }
  }
}