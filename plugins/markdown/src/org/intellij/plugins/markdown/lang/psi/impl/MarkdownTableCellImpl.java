package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MarkdownTableCellImpl extends MarkdownCompositePsiElementBase {
  public MarkdownTableCellImpl(@NotNull ASTNode node) {
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
      if (sibling instanceof MarkdownTableCellImpl) {
        index += 1;
      }
    }
    return index;
  }

  public @Nullable MarkdownTableImpl getParentTable() {
    return PsiTreeUtil.getParentOfType(this, MarkdownTableImpl.class);
  }

  public @Nullable MarkdownTableRowImpl getParentRow() {
    return PsiTreeUtil.getParentOfType(this, MarkdownTableRowImpl.class);
  }
}
