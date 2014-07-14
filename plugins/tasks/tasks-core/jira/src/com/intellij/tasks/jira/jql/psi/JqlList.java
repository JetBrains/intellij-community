package com.intellij.tasks.jira.jql.psi;

/**
 * @author Mikhail Golubev
 */
public interface JqlList extends JqlOperand {
  JqlOperand[] getElements();
}
