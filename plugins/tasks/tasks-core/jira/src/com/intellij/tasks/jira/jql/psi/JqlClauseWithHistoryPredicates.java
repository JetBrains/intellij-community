package com.intellij.tasks.jira.jql.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public interface JqlClauseWithHistoryPredicates extends JqlElement {
  @Nullable
  JqlOperand getAfter();

  @Nullable
  JqlOperand getBefore();

  @Nullable
  JqlOperand getOn();

  @Nullable
  JqlOperand getBy();

  @Nullable
  JqlOperand getDuring();

  @Nullable
  JqlOperand getFrom();

  @Nullable
  JqlOperand getTo();

  @NotNull
  JqlHistoryPredicate[] getHistoryPredicates();
}
