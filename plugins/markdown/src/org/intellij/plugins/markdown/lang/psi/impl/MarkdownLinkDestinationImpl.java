package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.IncorrectOperationException;
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.jetbrains.annotations.NotNull;

public class MarkdownLinkDestinationImpl extends ASTWrapperPsiElement implements MarkdownPsiElement {
  public MarkdownLinkDestinationImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MarkdownElementVisitor) {
      ((MarkdownElementVisitor)visitor).visitLinkDestination(this);
      return;
    }

    super.accept(visitor);
  }

  @NotNull
  @Override
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }

  public static class Manipulator extends AbstractElementManipulator<MarkdownLinkDestinationImpl> {

    @Override
    public MarkdownLinkDestinationImpl handleContentChange(@NotNull MarkdownLinkDestinationImpl element,
                                                           @NotNull TextRange range,
                                                           String newContent) throws IncorrectOperationException {
      final PsiElement child = element.getFirstChild();
      if (child instanceof LeafPsiElement) {
        ((LeafPsiElement)child).replaceWithText(range.replace(child.getText(), newContent));
      }
      else {
        throw new IncorrectOperationException("Bad child");
      }

      return element;
    }

    @NotNull
    @Override
    public TextRange getRangeInElement(@NotNull MarkdownLinkDestinationImpl element) {
      final String text = element.getText();
      if (text.startsWith("<") && text.endsWith(">")) {
        return TextRange.create(1, text.length() - 1);
      }
      else {
        return TextRange.allOf(text);
      }
    }
  }
}
