package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.tasks.jira.jql.psi.JqlClause;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import com.intellij.tasks.jira.jql.psi.JqlSubClause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JqlSubClauseImpl extends JqlElementImpl implements JqlSubClause {
  public JqlSubClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(JqlElementVisitor visitor) {
    visitor.visitJqlSubClause(this);
  }

  @Nullable
  @Override
  public JqlClause getInnerClause() {
    return findChildByClass(JqlClause.class);
  }
}
