// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.prevLeafs
import com.jetbrains.python.requirements.psi.NameReq
import com.jetbrains.python.requirements.psi.RequirementsTypes
import com.jetbrains.python.requirements.psi.VersionStmt

class RequirementsVersionCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.originalFile !is RequirementsFile)
      return

    val position = parameters.position
    val parent = position.parent
    val project = parameters.editor.project ?: return

    val packageName = getNameBySiblings(position) ?: getNameByParent(parent) ?: return
    val sdk = getPythonSdk(parameters.originalFile) ?: return
    completeVersions(packageName, project, sdk, result, false)
  }

  private fun getNameByParent(parent: PsiElement?): String? {
    if (parent !is VersionStmt)
      return null
    return PsiTreeUtil.getParentOfType(parent, NameReq::class.java)?.name?.text
  }

  private fun getNameBySiblings(position: PsiElement): String? {
    if ((position as? LeafPsiElement)?.elementType != RequirementsTypes.VERSION)
      return null

    val identifier = position.prevLeafs.firstOrNull { it.elementType == RequirementsTypes.IDENTIFIER }

    return identifier?.text
  }
}