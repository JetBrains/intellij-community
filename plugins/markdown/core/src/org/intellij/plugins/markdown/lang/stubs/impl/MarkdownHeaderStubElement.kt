package org.intellij.plugins.markdown.lang.stubs.impl

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.stubs.MarkdownStubElementBase

class MarkdownHeaderStubElement(
  parent: StubElement<*>,
  elementType: IStubElementType<*, *>,
  val indexedName: String?,
  val indexedAnchorReference: String?
): MarkdownStubElementBase<MarkdownHeader?>(parent, elementType)
