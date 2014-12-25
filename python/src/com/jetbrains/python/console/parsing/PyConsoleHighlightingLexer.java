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

import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author traff
 */
public class PyConsoleHighlightingLexer extends PythonHighlightingLexer {
  public PyConsoleHighlightingLexer(LanguageLevel languageLevel) {
    super(languageLevel);
  }

  @Override
  /**
   * Treats special symbols used in IPython console
   */
  public IElementType getTokenType() {
    IElementType type = super.getTokenType();
    if (type == PyTokenTypes.BAD_CHARACTER && PythonConsoleLexer.isSpecialSymbols(getTokenText())) {
      type = PythonConsoleLexer.getElementType(getTokenText());
    }

    return type;
  }
}
