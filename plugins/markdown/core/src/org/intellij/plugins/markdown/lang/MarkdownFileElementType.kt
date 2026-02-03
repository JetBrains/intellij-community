package org.intellij.plugins.markdown.lang

import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.tree.IStubFileElementType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

open class MarkdownFileElementType: IStubFileElementType<PsiFileStub<MarkdownFile>>(
  "MarkdownFile",
  MarkdownLanguage.INSTANCE
)
