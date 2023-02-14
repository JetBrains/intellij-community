package org.intellij.plugins.markdown.lang.stubs.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.intellij.plugins.markdown.lang.index.HeaderAnchorIndex;
import org.intellij.plugins.markdown.lang.index.HeaderTextIndex;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader;
import org.intellij.plugins.markdown.lang.stubs.MarkdownStubElementType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class MarkdownHeaderStubElementType extends MarkdownStubElementType<MarkdownHeaderStubElement, MarkdownHeader> {
  private static final Logger LOG = Logger.getInstance(MarkdownHeaderStubElementType.class);

  public MarkdownHeaderStubElementType(@NotNull String debugName) {
    super(debugName);
  }

  @NotNull
  @Override
  public PsiElement createElement(@NotNull ASTNode node) {
    return new MarkdownHeader(node);
  }

  @Override
  public MarkdownHeader createPsi(@NotNull MarkdownHeaderStubElement stub) {
    return new MarkdownHeader(stub, this);
  }

  @NotNull
  @Override
  public MarkdownHeaderStubElement createStub(@NotNull MarkdownHeader psi, StubElement parentStub) {
    return new MarkdownHeaderStubElement(parentStub, this, psi.getName(), psi.getAnchorText());
  }

  @Override
  public void serialize(@NotNull MarkdownHeaderStubElement stub, @NotNull StubOutputStream dataStream) throws IOException {
    writeUTFFast(dataStream, stub.getIndexedName());
    writeUTFFast(dataStream, stub.getIndexedAnchorReference());
  }

  private static void writeUTFFast(@NotNull StubOutputStream dataStream, String text) throws IOException {
    if (text == null) text = "";
    dataStream.writeUTFFast(text);
  }

  @NotNull
  @Override
  public MarkdownHeaderStubElement deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) {
    String indexedName = null;
    String indexedAnchorReference = null;
    try {
      indexedName = dataStream.readUTFFast();
      indexedAnchorReference = dataStream.readUTFFast();
    }
    catch (IOException e) {
      LOG.error("Cannot read data stream; ", e.getMessage());
    }

    final var actualName = StringUtil.isEmpty(indexedName) ? null : indexedName;
    final var actualAnchorReference = StringUtil.isEmpty(indexedAnchorReference) ? null : indexedAnchorReference;
    return new MarkdownHeaderStubElement(
      parentStub,
      this,
      actualName,
      actualAnchorReference
    );
  }

  @Override
  public void indexStub(@NotNull MarkdownHeaderStubElement stub, @NotNull IndexSink sink) {
    final var indexedName = stub.getIndexedName();
    if (indexedName != null) {
      sink.occurrence(HeaderTextIndex.KEY, indexedName);
    }
    final var indexedAnchorReference = stub.getIndexedAnchorReference();
    if (indexedAnchorReference != null) {
      sink.occurrence(HeaderAnchorIndex.KEY, indexedAnchorReference);
    }
  }
}
