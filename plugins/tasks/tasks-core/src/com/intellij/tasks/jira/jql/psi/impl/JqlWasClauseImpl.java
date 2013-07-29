package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.tasks.jira.jql.JqlTokenTypes;
import com.intellij.tasks.jira.jql.psi.JqlElementVisitor;
import com.intellij.tasks.jira.jql.psi.JqlWasClause;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlWasClauseImpl extends JqlClauseWithHistoryPredicatesImpl implements JqlWasClause {
  public JqlWasClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(JqlElementVisitor visitor) {
    visitor.visitJqlWasClause(this);
  }

  @NotNull
  @Override
  public Type getType() {
    boolean hasNot = getNode().findChildByType(JqlTokenTypes.NOT_KEYWORD) != null;
    boolean hasIn = getNode().findChildByType(JqlTokenTypes.IN_KEYWORD) != null;
    if (hasIn && hasNot) {
      return Type.WAS_NOT_IN;
    }
    else if (hasIn) {
      return Type.WAS_IN;
    }
    else if (hasNot) {
      return Type.WAS_NOT;
    }
    else {
      return Type.WAS;
    }
  }
}
