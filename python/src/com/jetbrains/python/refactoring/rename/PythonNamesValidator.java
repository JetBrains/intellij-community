// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.rename;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.lexer.PythonLexer;
import org.jetbrains.annotations.NotNull;

public class PythonNamesValidator implements NamesValidator {
  private static final PythonLexer ourLexer = new PythonLexer();

  @Override
  public synchronized boolean isKeyword(final @NotNull String name, final Project project) {
    try {
      ourLexer.start(name);
      if (!PythonDialectsTokenSetProvider.getInstance().getKeywordTokens().contains(ourLexer.getTokenType())) {
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
  public synchronized boolean isIdentifier(final @NotNull String name, final Project project) {
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
