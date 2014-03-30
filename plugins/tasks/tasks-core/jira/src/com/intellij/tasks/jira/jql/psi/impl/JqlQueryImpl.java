package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.tasks.jira.jql.psi.JqlClause;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import com.intellij.tasks.jira.jql.psi.JqlOrderBy;
import com.intellij.tasks.jira.jql.psi.JqlQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Nullable
  @Override
  public JqlOrderBy getOrderBy() {
    return findChildByClass(JqlOrderBy.class);
  }
}
