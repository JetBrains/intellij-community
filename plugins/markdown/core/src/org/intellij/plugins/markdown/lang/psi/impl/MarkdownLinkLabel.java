// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.model.psi.PsiExternalReferenceHost;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.util.IncorrectOperationException;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class MarkdownLinkLabel extends ASTWrapperPsiElement implements MarkdownPsiElement, PsiExternalReferenceHost {
  public MarkdownLinkLabel(@NotNull ASTNode node) {
    super(node);
  }

  // Returns the label text without the surrounding brackets, e.g. "[foo]" -> "foo"
  public @NotNull String getLabelText() {
    return getLabelTextRange().substring(getText());
  }

  @ApiStatus.Internal
  public @NotNull TextRange getLabelTextRange() {
    String text = getText();
    int startOffset = text.startsWith("[") ? 1 : 0;
    int endOffset = text.endsWith("]") ? text.length() - 1 : text.length();
    return new TextRange(startOffset, endOffset);
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }

  @ApiStatus.Internal
  public static final class Manipulator extends AbstractElementManipulator<MarkdownLinkLabel> {
    @Override
    public @NotNull MarkdownLinkLabel handleContentChange(@NotNull MarkdownLinkLabel element,
                                                          @NotNull TextRange range,
                                                          String newContent) throws IncorrectOperationException {
      String updatedText = range.replace(element.getText(), newContent);
      CompositeElement node = ASTFactory.composite(MarkdownElementTypes.LINK_LABEL);
      node.rawAddChildren(ASTFactory.leaf(MarkdownTokenTypes.LBRACKET, "["));
      node.rawAddChildren(ASTFactory.leaf(MarkdownTokenTypes.TEXT, updatedText.substring(1, updatedText.length() - 1)));
      node.rawAddChildren(ASTFactory.leaf(MarkdownTokenTypes.RBRACKET, "]"));
      CodeEditUtil.setNodeGeneratedRecursively(node, true);
      MarkdownLinkLabel replacement = new MarkdownLinkLabel(node);
      element.getNode().getTreeParent().replaceChild(element.getNode(), node);
      return replacement;
    }

    @Override
    public @NotNull TextRange getRangeInElement(@NotNull MarkdownLinkLabel element) {
      return element.getLabelTextRange();
    }
  }
}
