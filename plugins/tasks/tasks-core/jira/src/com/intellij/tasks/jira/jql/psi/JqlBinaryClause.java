package com.intellij.tasks.jira.jql.psi;

/**
 * @author Mikhail Golubev
 */
public interface JqlBinaryClause extends JqlClause {
  JqlClause getLeftSubClause();
  JqlClause getRightSubClause();
}
