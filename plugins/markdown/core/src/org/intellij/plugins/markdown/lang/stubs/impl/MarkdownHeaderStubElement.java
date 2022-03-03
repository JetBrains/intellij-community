package org.intellij.plugins.markdown.lang.stubs.impl;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader;
import org.intellij.plugins.markdown.lang.stubs.MarkdownStubElementBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MarkdownHeaderStubElement extends MarkdownStubElementBase<MarkdownHeader> {
  @Nullable private final String myName;

  protected MarkdownHeaderStubElement(@NotNull StubElement parent,
                                      @NotNull IStubElementType elementType,
                                      @Nullable String indexedName) {
    super(parent, elementType);
    myName = indexedName;
  }

  @Nullable
  String getIndexedName() {
    return myName;
  }
}
