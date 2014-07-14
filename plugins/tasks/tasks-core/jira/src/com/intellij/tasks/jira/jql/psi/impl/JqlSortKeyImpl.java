package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.tasks.jira.jql.JqlTokenTypes;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import com.intellij.tasks.jira.jql.psi.JqlSortKey;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlSortKeyImpl extends JqlElementImpl implements JqlSortKey {
  public JqlSortKeyImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(JqlElementVisitor visitor) {
    visitor.visitJqlSortKey(this);
  }

  @NotNull
  @Override
  public String getFieldName() {
    PsiElement fieldNode = getFirstChild();
    assert fieldNode != null;
    return unescape(fieldNode.getText());
  }

  @Override
  public boolean isAscending() {
    PsiElement order = findChildByType(JqlTokenTypes.SORT_ORDERS);
    return order == null || order.getNode().getElementType() == JqlTokenTypes.ASC_KEYWORD;
  }
}
