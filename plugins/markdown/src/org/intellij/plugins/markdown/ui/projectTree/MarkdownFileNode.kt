// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.projectTree

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.lang.MarkdownFileType

class MarkdownFileNode(private val nodeChildren: Collection<PsiFile>) : Iterable<PsiElement> {
  companion object {
    val DATA_KEY: DataKey<MarkdownFileNode> = DataKey.create("markdown.node.files")
  }

  val name: String
    get() = nodeChildren.find { it.virtualFile.fileType is MarkdownFileType }!!.virtualFile.nameWithoutExtension

  override fun iterator(): Iterator<PsiElement> = nodeChildren.iterator()
}
