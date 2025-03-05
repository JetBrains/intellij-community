// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @Nullable JqlOperand getAfter() {
    return findOperandOfPredicate(JqlHistoryPredicate.Type.AFTER);
  }

  @Override
  public @Nullable JqlOperand getBefore() {
    return findOperandOfPredicate(JqlHistoryPredicate.Type.BEFORE);
  }

  @Override
  public @Nullable JqlOperand getOn() {
    return findOperandOfPredicate(JqlHistoryPredicate.Type.ON);
  }

  @Override
  public @Nullable JqlOperand getBy() {
    return findOperandOfPredicate(JqlHistoryPredicate.Type.BY);
  }

  @Override
  public @Nullable JqlOperand getDuring() {
    return findOperandOfPredicate(JqlHistoryPredicate.Type.DURING);
  }

  @Override
  public @Nullable JqlOperand getFrom() {
    return findOperandOfPredicate(JqlHistoryPredicate.Type.FROM);
  }

  @Override
  public @Nullable JqlOperand getTo() {
    return findOperandOfPredicate(JqlHistoryPredicate.Type.TO);
  }

  @Override
  public JqlHistoryPredicate @NotNull [] getHistoryPredicates() {
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
