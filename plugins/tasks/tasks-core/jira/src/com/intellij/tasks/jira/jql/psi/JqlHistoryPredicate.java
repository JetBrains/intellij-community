package com.intellij.tasks.jira.jql.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public interface JqlHistoryPredicate extends JqlElement {
  enum Type {
    FROM,
    TO,
    BEFORE,
    AFTER,
    BY,
    ON,
    DURING
  }

  @NotNull
  Type getType();

  @Nullable
  JqlOperand getOperand();
}
