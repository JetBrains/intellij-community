package com.intellij.tasks.jira.jql.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public interface JqlOrderBy extends JqlElement {
  @NotNull
  JqlSortKey[] getSortKeys();
}
