/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
