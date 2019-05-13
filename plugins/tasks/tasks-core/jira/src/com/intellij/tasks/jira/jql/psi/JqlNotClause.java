package com.intellij.tasks.jira.jql.psi;

/**
 * @author Mikhail Golubev
 */
public interface JqlNotClause extends JqlClause {
  JqlClause getSubClause();
}
