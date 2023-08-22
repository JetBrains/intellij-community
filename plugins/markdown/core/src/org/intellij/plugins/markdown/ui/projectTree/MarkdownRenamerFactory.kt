// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.projectTree

import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.usageView.UsageInfo
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class MarkdownRenamerFactory : AutomaticRenamerFactory {
  override fun isApplicable(element: PsiElement): Boolean {
    if (element !is MarkdownFile) {
      return false
    }
    val boundDocuments = MarkdownRenamer.findBoundDocument(element)
    return boundDocuments.isNotEmpty()
  }

  override fun getOptionName(): String = MarkdownBundle.message("markdown.rename.factory.option.name")

  override fun isEnabled(): Boolean = true

  override fun setEnabled(enabled: Boolean) {}

  override fun createRenamer(element: PsiElement, newName: String, usages: MutableCollection<UsageInfo>): AutomaticRenamer {
    check(element is MarkdownFile)
    return MarkdownRenamer(element, newName)
  }
}
