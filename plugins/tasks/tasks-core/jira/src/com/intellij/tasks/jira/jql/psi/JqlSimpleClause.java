package com.intellij.tasks.jira.jql.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Simple clauses such as EQ, NE, GT, LT, GE, LE, CONTAINS, NOT_CONTAINS, IN, NOT_IN, IS and IS_NOT
 *
 * @author Mikhail Golubev
 */
public interface JqlSimpleClause extends JqlTerminalClause {
  @Nullable
  JqlOperand getOperand();
}
