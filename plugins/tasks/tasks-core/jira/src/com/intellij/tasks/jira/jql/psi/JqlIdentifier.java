package com.intellij.tasks.jira.jql.psi;

/**
 * @author Mikhail Golubev
 */
public interface JqlIdentifier extends JqlElement {
  String getEscapedText();

  boolean isCustomField();
}
