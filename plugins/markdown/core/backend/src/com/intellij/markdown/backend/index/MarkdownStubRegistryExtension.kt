package com.intellij.markdown.backend.index

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.stubs.StubRegistry
import com.intellij.psi.stubs.StubRegistryExtension
import com.intellij.psi.stubs.StubSerializingElementFactory
import com.intellij.psi.tree.IElementType
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.stubs.impl.MarkdownHeaderStubElement

internal class MarkdownStubRegistryExtension : StubRegistryExtension {
  override fun register(registry: StubRegistry) {
    HEADER_TYPES.forEach { elementType ->
      registry.registerStubSerializingFactory(elementType, MarkdownHeaderStubSerializingFactory(elementType))
    }
  }
}

private val HEADER_TYPES = arrayOf(
  MarkdownElementTypes.SETEXT_1,
  MarkdownElementTypes.SETEXT_2,
  MarkdownElementTypes.ATX_1,
  MarkdownElementTypes.ATX_2,
  MarkdownElementTypes.ATX_3,
  MarkdownElementTypes.ATX_4,
  MarkdownElementTypes.ATX_5,
  MarkdownElementTypes.ATX_6,
)

private class MarkdownHeaderStubSerializingFactory(
  private val elementType: IElementType,
) : StubSerializingElementFactory<MarkdownHeaderStubElement, MarkdownHeader> {
  override fun createStub(psi: MarkdownHeader, parentStub: StubElement<out PsiElement>?): MarkdownHeaderStubElement {
    return MarkdownHeaderStubElement(parentStub, elementType, psi.name, psi.anchorText)
  }

  override fun createPsi(stub: MarkdownHeaderStubElement): MarkdownHeader {
    return MarkdownHeader(stub, elementType)
  }

  override fun getExternalId(): String = "markdown.${elementType.debugName}"

  override fun serialize(stub: MarkdownHeaderStubElement, dataStream: StubOutputStream) {
    dataStream.writeUTFFast(stub.indexedName ?: "")
    dataStream.writeUTFFast(stub.indexedAnchorReference ?: "")
  }

  override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): MarkdownHeaderStubElement {
    return MarkdownHeaderStubElement(
      parentStub,
      elementType,
      dataStream.readUTFFast().takeUnless { it.isEmpty() },
      dataStream.readUTFFast().takeUnless { it.isEmpty() },
    )
  }

  override fun indexStub(stub: MarkdownHeaderStubElement, sink: IndexSink) {
    stub.indexedName?.let { sink.occurrence(HeaderTextIndex.KEY, it) }
    stub.indexedAnchorReference?.let { sink.occurrence(HeaderAnchorIndex.KEY, it) }
  }
}
