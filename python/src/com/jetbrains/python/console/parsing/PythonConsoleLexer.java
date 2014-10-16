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
package com.jetbrains.python.console.parsing;

import com.google.common.collect.ImmutableMap;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonIndentingLexer;
import com.jetbrains.python.psi.PyElementType;

import java.util.Map;

/**
 * @author traff
 */
public class PythonConsoleLexer extends PythonIndentingLexer {
  private final static Map<String, PyElementType> SPECIAL_IPYTHON_SYMBOLS = ImmutableMap.of("?", PyConsoleTokenTypes
    .QUESTION_MARK, "!", PyConsoleTokenTypes.PLING);

  @Override
  /**
   * Treats special symbols used in IPython console
   */
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
