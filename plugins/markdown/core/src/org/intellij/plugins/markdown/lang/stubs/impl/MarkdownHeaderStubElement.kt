package org.intellij.plugins.markdown.lang.stubs.impl

import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.stubs.MarkdownStubElementBase

class MarkdownHeaderStubElement(
  parent: StubElement<*>?,
  elementType: IElementType,
  val indexedName: String?,
  val indexedAnchorReference: String?
): MarkdownStubElementBase<MarkdownHeader?>(parent, elementType)
