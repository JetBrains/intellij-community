// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiReference
import org.intellij.plugins.markdown.lang.references.MarkdownPathReferenceProvider

class MarkdownAtPath(node: ASTNode) : MarkdownCompositePsiElementBase(node), PsiExternalReferenceHost {
  override fun getPresentableTagName(): String = "at_path"

  override fun getReferences(): Array<PsiReference> {
    val contentRange = TextRange(1, textLength)
    val content = contentRange.substring(text)
    return MarkdownPathReferenceProvider.getAtPathReferences(this, contentRange, content)
  }
}
