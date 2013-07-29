package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.tasks.jira.jql.psi.JqlChangedClause;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlChangedClauseImpl extends JqlClauseWithHistoryPredicatesImpl implements JqlChangedClause {
  public JqlChangedClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(JqlElementVisitor visitor) {
    visitor.visitJqlChangedClause(this);
  }

  @NotNull
  @Override
  public Type getType() {
    return Type.CHANGED;
  }
}
