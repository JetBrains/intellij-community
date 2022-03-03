package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MarkdownTableCell extends MarkdownCompositePsiElementBase {
  public MarkdownTableCell(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String getPresentableTagName() {
    return "td";
  }

  public int getColumnIndex() {
    var sibling = getPrevSibling();
    var index = 0;
    while (sibling != null) {
      sibling = sibling.getPrevSibling();
      if (sibling instanceof MarkdownTableCell) {
        index += 1;
      }
    }
    return index;
  }

  public @Nullable MarkdownTable getParentTable() {
    return PsiTreeUtil.getParentOfType(this, MarkdownTable.class);
  }

  public @Nullable MarkdownTableRow getParentRow() {
    return PsiTreeUtil.getParentOfType(this, MarkdownTableRow.class);
  }
}
