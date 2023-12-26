// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.requirements

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.python.community.impl.requirements.psi.NameReq

class RequirementsVersionCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val position = parameters.position
    val parent = position.parent
    val project = parameters.editor.project ?: return

    if (parent is com.intellij.python.community.impl.requirements.psi.VersionStmt) {
      val name = PsiTreeUtil.getParentOfType(parent, NameReq::class.java)?.simpleName?.text ?: return
      completeVersions(name, project, result)
    }
  }
}