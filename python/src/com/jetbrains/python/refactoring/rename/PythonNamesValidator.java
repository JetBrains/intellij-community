// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.rename;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.lexer.PythonLexer;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexey.Ivanov
 */
public class PythonNamesValidator implements NamesValidator {
  private static final PythonLexer ourLexer = new PythonLexer();

  @Override
  public synchronized boolean isKeyword(@NotNull final String name, final Project project) {
    try {
      ourLexer.start(name);
      if (!PythonDialectsTokenSetProvider.INSTANCE.getKeywordTokens().contains(ourLexer.getTokenType())) {
        return false;
      }
      ourLexer.advance();
      return ourLexer.getTokenType() == null;
    }
    catch (StringIndexOutOfBoundsException e) {
      return false;
    }
  }

  @Override
  public synchronized boolean isIdentifier(@NotNull final String name, final Project project) {
    try {
      ourLexer.start(name);
      if (ourLexer.getTokenType() != PyTokenTypes.IDENTIFIER) {
        return false;
      }
      ourLexer.advance();
      return ourLexer.getTokenType() == null;
    }
    catch (StringIndexOutOfBoundsException e) {
      return false;
    }
  }
}
