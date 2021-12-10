package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.intellij.plugins.markdown.structureView.MarkdownBasePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MarkdownListItem extends MarkdownCompositePsiElementBase {
  public MarkdownListItem(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  public PsiElement getMarkerElement() {
    final PsiElement child = getFirstChild();
    if (child != null && MarkdownTokenTypeSets.LIST_MARKERS.contains(child.getNode().getElementType())) {
      return child;
    }
    else {
      return null;
    }
  }

  @Nullable
  public PsiElement getCheckBox() {
    final PsiElement markerElement = getMarkerElement();
    if (markerElement == null) {
      return null;
    }
    final PsiElement candidate = markerElement.getNextSibling();
    if (candidate != null && candidate.getNode().getElementType() == MarkdownTokenTypes.CHECK_BOX) {
      return candidate;
    }
    else {
      return null;
    }
  }

  @Override
  public ItemPresentation getPresentation() {
    return new MyItemPresentation();
  }

  @Override
  public String getPresentableTagName() {
    return "li";
  }

  private class MyItemPresentation extends MarkdownBasePresentation implements ColoredItemPresentation {
    @Nullable
    @Override
    public String getPresentableText() {
      if (!isValid()) {
        return null;
      }
      final PsiElement markerElement = getMarkerElement();
      if (markerElement == null) {
        return null;
      }
      return markerElement.getText().trim();
    }

    @Nullable
    @Override
    public String getLocationString() {
      if (!isValid()) {
        return null;
      }

      if (ContainerUtil.getFirstItem(getCompositeChildren()) instanceof MarkdownParagraphImpl) {
        final MarkdownCompositePsiElementBase element = findChildByClass(MarkdownCompositePsiElementBase.class);
        assert element != null;
        return StringUtil.shortenTextWithEllipsis(element.getText(), PRESENTABLE_TEXT_LENGTH, 0);
      }
      else {
        return null;
      }
    }

    @Nullable
    @Override
    public Icon getIcon(boolean unused) {
      return null;
    }

    @Nullable
    @Override
    public TextAttributesKey getTextAttributesKey() {
      final PsiElement checkBox = getCheckBox();
      if (checkBox == null) {
        return null;
      }
      if (checkBox.textContains('x') || checkBox.textContains('X')) {
        return ConsoleHighlighter.GREEN;
      }
      else {
        return ConsoleHighlighter.RED;
      }
    }
  }
}
