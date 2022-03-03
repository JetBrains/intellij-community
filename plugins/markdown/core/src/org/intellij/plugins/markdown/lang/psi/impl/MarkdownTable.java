package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MarkdownTable extends MarkdownCompositePsiElementBase {
  public MarkdownTable(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MarkdownElementVisitor) {
      ((MarkdownElementVisitor)visitor).visitTable(this);
      return;
    }

    super.accept(visitor);
  }

  @Override
  public String getPresentableTagName() {
    return "table";
  }

  public @Nullable MarkdownTableRow getHeaderRow() {
    // FIXME: Explicitly check for first element
    return PsiTreeUtil.getChildOfType(this, MarkdownTableRow.class);
  }

  public @NotNull List<@NotNull MarkdownTableRow> getRows(boolean includeHeader) {
    final var rows = PsiTreeUtil.getChildrenOfType(this, MarkdownTableRow.class);
    if (rows == null) {
      return ContainerUtil.emptyList();
    }
    if (!includeHeader) {
      return ContainerUtil.filter(rows, row -> PsiUtilCore.getElementType(row) != MarkdownElementTypes.TABLE_HEADER);
    }
    return ContainerUtil.immutableList(rows);
  }
}
