package com.intellij.tasks.jira.jql.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public interface JqlSortKey extends JqlElement {
  @NotNull
  String getFieldName();

  boolean isAscending();
}
