package com.intellij.tasks.jira.jql.psi;

/**
 * @author Mikhail Golubev
 */
public interface JqlLiteral extends JqlOperand {
  String getEscapedText();

  String getOriginalText();
}
