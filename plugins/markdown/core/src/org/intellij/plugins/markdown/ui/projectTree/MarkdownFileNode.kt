// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.projectTree

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

internal class MarkdownFileNode(val name: String, private val children: Collection<PsiFile>) : Iterable<PsiElement> {
  override fun iterator(): Iterator<PsiElement> = children.iterator()
}
