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

import com.google.common.collect.ImmutableSet;
import com.intellij.lang.SyntaxTreeBuilder;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.psi.LanguageLevel;

public class PyConsoleParser extends PyParser {

  private static final ImmutableSet<IElementType> IPYTHON_START_SYMBOLS = new ImmutableSet.Builder<IElementType>().add(
    PyConsoleTokenTypes.PLING,
    PyConsoleTokenTypes.QUESTION_MARK,
    PyTokenTypes.COMMA,
    PyTokenTypes.DIV,
    PyTokenTypes.PERC,
    PyTokenTypes.SEMICOLON
    ).build();

  private final PythonConsoleData myPythonConsoleData;

  public PyConsoleParser(PythonConsoleData pythonConsoleData, LanguageLevel languageLevel) {
    myPythonConsoleData = pythonConsoleData;
    myLanguageLevel = languageLevel;
  }

  public static boolean startsWithIPythonSpecialSymbol(SyntaxTreeBuilder builder) {
    IElementType tokenType = builder.getTokenType();
    return IPYTHON_START_SYMBOLS.contains(tokenType);
  }

  @Override
  protected ParsingContext createParsingContext(SyntaxTreeBuilder builder, LanguageLevel languageLevel) {
    boolean iPythonStartSymbol = myPythonConsoleData.isIPythonEnabled() && startsWithIPythonSpecialSymbol(builder);
    return new PyConsoleParsingContext(builder, languageLevel, myPythonConsoleData, iPythonStartSymbol);
  }
}
