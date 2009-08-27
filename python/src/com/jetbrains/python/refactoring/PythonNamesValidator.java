package com.jetbrains.python.refactoring;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonLexer;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 19, 2009
 * Time: 10:23:26 PM
 */
public class PythonNamesValidator implements NamesValidator {
  private static final PythonLexer ourLexer = new PythonLexer();

  public synchronized boolean isKeyword(@NotNull final String name, final Project project) {
    ourLexer.start(name);
    if (!PyTokenTypes.KEYWORDS.contains(ourLexer.getTokenType())) {
      return false;
    }
    ourLexer.advance();
    return ourLexer.getTokenType() == null;
  }

  public synchronized boolean isIdentifier(@NotNull final String name, final Project project) {
    ourLexer.start(name);
    if (ourLexer.getTokenType() != PyTokenTypes.IDENTIFIER) {
      return false;
    }
    ourLexer.advance();
    return ourLexer.getTokenType() == null;
  }
}
