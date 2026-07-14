package org.intellij.plugins.markdown.lang.stubs;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class MarkdownStubBasedPsiElementBase<Stub extends MarkdownStubElement<?>>
  extends StubBasedPsiElementBase<Stub> implements MarkdownPsiElement, StubBasedPsiElement<Stub> {

  public MarkdownStubBasedPsiElementBase(final Stub stub, IElementType nodeType) {
    super(stub, nodeType);
  }

  public MarkdownStubBasedPsiElementBase(final ASTNode node) {
    super(node);
  }

  @Override
  public IElementType getIElementType() {
    return getElementTypeImpl();
  }

  @Override
  @Deprecated
  @SuppressWarnings("deprecation")
  public @NotNull IStubElementType<?, ?> getElementType() {
    throw new UnsupportedOperationException("Use getIElementType() instead");
  }

  @Override
  public String toString() {
    return getIElementType().toString();
  }
}