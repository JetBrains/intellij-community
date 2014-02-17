package com.intellij.tasks.jira.jql.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public interface JqlFunctionCall extends JqlOperand {
  @NotNull
  JqlIdentifier getFunctionName();

  @NotNull
  JqlArgumentList getArgumentList();
}
