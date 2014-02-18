package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.tasks.jira.jql.psi.JqlAndClause;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlAndClauseImpl extends JqlBinaryClauseImpl implements JqlAndClause {
  public JqlAndClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(JqlElementVisitor visitor) {
    visitor.visitJqlAndClause(this);
  }
}
