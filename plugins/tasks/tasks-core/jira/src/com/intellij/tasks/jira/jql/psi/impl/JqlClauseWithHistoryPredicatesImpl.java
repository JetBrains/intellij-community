package com.intellij.tasks.jira.jql.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.tasks.jira.jql.psi.JqlClauseWithHistoryPredicates;
import com.intellij.tasks.jira.jql.psi.JqlHistoryPredicate;
import com.intellij.tasks.jira.jql.psi.JqlOperand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public abstract class JqlClauseWithHistoryPredicatesImpl extends JqlTerminalClauseImpl implements JqlClauseWithHistoryPredicates {
  public JqlClauseWithHistoryPredicatesImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public JqlOperand getAfter() {
    return findOperandOfPredicate(JqlHistoryPredicate.Type.AFTER);
  }

  @Nullable
  @Override
  public JqlOperand getBefore() {
    return findOperandOfPredicate(JqlHistoryPredicate.Type.BEFORE);
  }

  @Nullable
  @Override
  public JqlOperand getOn() {
    return findOperandOfPredicate(JqlHistoryPredicate.Type.ON);
  }

  @Nullable
  @Override
  public JqlOperand getBy() {
    return findOperandOfPredicate(JqlHistoryPredicate.Type.BY);
  }

  @Nullable
  @Override
  public JqlOperand getDuring() {
    return findOperandOfPredicate(JqlHistoryPredicate.Type.DURING);
  }

  @Nullable
  @Override
  public JqlOperand getFrom() {
    return findOperandOfPredicate(JqlHistoryPredicate.Type.FROM);
  }

  @Nullable
  @Override
  public JqlOperand getTo() {
    return findOperandOfPredicate(JqlHistoryPredicate.Type.TO);
  }

  @NotNull
  @Override
  public JqlHistoryPredicate[] getHistoryPredicates() {
    return findChildrenByClass(JqlHistoryPredicate.class);
  }

  private JqlOperand findOperandOfPredicate(JqlHistoryPredicate.Type type) {
    for (JqlHistoryPredicate predicate : getHistoryPredicates()) {
      if (predicate.getType() == type) {
        return predicate.getOperand();
      }
    }
    return null;
  }
}
