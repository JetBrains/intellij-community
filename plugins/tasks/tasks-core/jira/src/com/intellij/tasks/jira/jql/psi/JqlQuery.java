package com.intellij.tasks.jira.jql.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public interface JqlQuery extends JqlElement {
  @Nullable
  JqlClause getClause();

  @Nullable
  JqlOrderBy getOrderBy();
}
