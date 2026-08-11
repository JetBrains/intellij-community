package org.intellij.plugins.markdown.lang.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;

public abstract class MarkdownStubElementBase<T extends PsiElement> extends StubBase<T> implements MarkdownStubElement<T> {
  protected MarkdownStubElementBase(StubElement parent, IElementType elementType) {
    super(parent, elementType);
  }
}