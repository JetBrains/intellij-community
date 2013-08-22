package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.tasks.jira.jql.psi.JqlClause;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import com.intellij.tasks.jira.jql.psi.JqlSortKey;
import com.intellij.tasks.jira.jql.psi.JqlQuery;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlQueryImpl extends JqlElementImpl implements JqlQuery {
  public JqlQueryImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(JqlElementVisitor visitor) {
    visitor.visitJqlQuery(this);
  }

  @Override
  public JqlClause getClause() {
    return findChildByClass(JqlClause.class);
  }

  @Override
  public boolean isOrdered() {
    return getOrderKeys().length != 0;
  }

  @Override
  public JqlSortKey[] getOrderKeys() {
    return findChildrenByClass(JqlSortKey.class);
  }
}
