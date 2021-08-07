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
package com.jetbrains.python.parsing.console;

import com.google.common.collect.ImmutableMap;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonIndentingLexer;
import com.jetbrains.python.lexer.PythonLexerKind;
import com.jetbrains.python.psi.PyElementType;

import java.util.Map;

public class PythonConsoleLexer extends PythonIndentingLexer {
  public PythonConsoleLexer() {
    super(PythonLexerKind.CONSOLE);
  }

  private final static Map<String, PyElementType> SPECIAL_IPYTHON_SYMBOLS = ImmutableMap.of(
    "?", PyConsoleTokenTypes.QUESTION_MARK,
    "!", PyConsoleTokenTypes.PLING,
    "$", PyConsoleTokenTypes.DOLLAR
  );

  /**
   * Treats special symbols used in IPython console
   */
  @Override
  public IElementType getTokenType() {
    IElementType type = super.getTokenType();
    if (type == PyTokenTypes.BAD_CHARACTER && isSpecialSymbols(getTokenText())) {
      type = getElementType(getTokenText());
    }

    return type;
  }

  public static PyElementType getElementType(String token) {
    return SPECIAL_IPYTHON_SYMBOLS.get(token);
  }

  public static boolean isSpecialSymbols(String token) {
    return SPECIAL_IPYTHON_SYMBOLS.containsKey(token);
  }
}
