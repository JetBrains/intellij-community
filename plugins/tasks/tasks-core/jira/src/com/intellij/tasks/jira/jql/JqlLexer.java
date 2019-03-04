package com.intellij.tasks.jira.jql;

import com.intellij.lexer.FlexAdapter;

/**
 * @author Mikhail Golubev
 */
public class JqlLexer extends FlexAdapter {
  public JqlLexer() {
    super(new _JqlLexer(null));
  }
}
