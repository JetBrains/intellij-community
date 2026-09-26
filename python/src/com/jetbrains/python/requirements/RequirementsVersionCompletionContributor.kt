// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.python.requirements.RequirementsFile
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.prevLeafs
import com.intellij.python.requirements.parser.psi.NameReq
import com.intellij.python.requirements.parser.psi.Version
import com.intellij.python.requirements.parser.psi.RequirementsTypes

class RequirementsVersionCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.originalFile !is RequirementsFile)
      return

    val position = parameters.position
    val parent = position.parent
    val project = parameters.editor.project ?: return

    val packageName = getNameBySiblings(position) ?: getNameByParent(parent) ?: return
    val sdk = getPythonSdk(parameters.originalFile) ?: return
    // `RequirementsReferenceContributor` attaches a reference to the surrounding `NameReq`
    // (covering `<name>==<version>`) so Quick Doc dispatch fires on extras / version / marker.
    // The platform's default completion-prefix derivation uses that reference's range and
    // returns `<name>==<typed>`, which would filter every version item out by prefix.
    // Force the prefix to just the typed-version portion (text inside the position leaf up
    // to the caret) so version items match.
    val versionResult = result.withPrefixMatcher(versionPrefix(position, parameters.offset))
    completeVersions(packageName, project, sdk, versionResult, false)
  }

  private fun getNameByParent(parent: PsiElement?): String? {
    if (parent !is Version)
      return null
    return PsiTreeUtil.getParentOfType(parent, NameReq::class.java)?.packageName?.text
  }

  private fun getNameBySiblings(position: PsiElement): String? {
    if ((position as? LeafPsiElement)?.elementType != RequirementsTypes.VERSION)
      return null

    val identifier = position.prevLeafs.firstOrNull { it.elementType == RequirementsTypes.PACKAGE_NAME }

    return identifier?.text
  }

  private fun versionPrefix(position: PsiElement, offsetInFile: Int): String {
    val offsetInPosition = (offsetInFile - position.textRange.startOffset).coerceIn(0, position.textLength)
    return position.text.take(offsetInPosition)
  }
}