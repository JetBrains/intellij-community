package com.intellij.tasks.jira.jql.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public interface JqlSubClause extends JqlClause {
  @Nullable
  JqlClause getInnerClause();
}
