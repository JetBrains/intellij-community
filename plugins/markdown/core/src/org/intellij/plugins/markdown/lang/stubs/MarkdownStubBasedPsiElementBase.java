package org.intellij.plugins.markdown.lang.stubs;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.stubs.IStubElementType;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;

public abstract class MarkdownStubBasedPsiElementBase<Stub extends MarkdownStubElement>
  extends StubBasedPsiElementBase<Stub> implements MarkdownPsiElement, StubBasedPsiElement<Stub> {

  public MarkdownStubBasedPsiElementBase(final Stub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public MarkdownStubBasedPsiElementBase(final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return getElementType().toString();
  }
}