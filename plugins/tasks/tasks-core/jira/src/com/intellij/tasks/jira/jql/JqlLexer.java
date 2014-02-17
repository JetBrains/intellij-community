package com.intellij.tasks.jira.jql;

import com.intellij.lexer.FlexAdapter;

import java.io.Reader;

/**
 * @author Mikhail Golubev
 */
public class JqlLexer extends FlexAdapter {
  public JqlLexer() {
    super(new _JqlLexer((Reader)null));
  }
}
