package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MarkdownTableRow extends MarkdownCompositePsiElementBase {
  public MarkdownTableRow(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String getPresentableTagName() {
    if (getNode().getElementType() == MarkdownElementTypes.TABLE_HEADER) {
      return "th";
    }
    else {
      return "tr";
    }
  }

  public @NotNull List<@NotNull MarkdownTableCell> getCells() {
    final var cells = PsiTreeUtil.getChildrenOfType(this, MarkdownTableCell.class);
    if (cells == null) {
      return ContainerUtil.emptyList();
    }
    return ContainerUtil.immutableList(cells);
  }

  public @Nullable MarkdownTableCell getCell(int nth) {
    final var cells = getCells();
    if (cells.size() <= nth) {
      return null;
    }
    return cells.get(nth);
  }

  public @Nullable MarkdownTable getParentTable() {
    return PsiTreeUtil.getParentOfType(this, MarkdownTable.class);
  }
}
