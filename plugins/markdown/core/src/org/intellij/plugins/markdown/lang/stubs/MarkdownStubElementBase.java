package org.intellij.plugins.markdown.lang.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public abstract class MarkdownStubElementBase<T extends PsiElement> extends StubBase<T> implements MarkdownStubElement<T> {
  protected MarkdownStubElementBase(StubElement parent, IStubElementType elementType) {
    super(parent, elementType);
  }
}