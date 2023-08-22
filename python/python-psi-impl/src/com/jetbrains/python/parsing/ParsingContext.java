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
package com.jetbrains.python.parsing;

import com.intellij.lang.SyntaxTreeBuilder;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

public class ParsingContext {
  private final StatementParsing stmtParser;
  private final ExpressionParsing expressionParser;
  private final FunctionParsing functionParser;
  private final PatternParsing patternParser;
  private final SyntaxTreeBuilder myBuilder;
  private final LanguageLevel myLanguageLevel;
  private final Deque<ParsingScope> myScopes;

  public ParsingContext(final SyntaxTreeBuilder builder, LanguageLevel languageLevel) {
    myBuilder = builder;
    myLanguageLevel = languageLevel;
    stmtParser = new StatementParsing(this);
    expressionParser = new ExpressionParsing(this);
    functionParser = new FunctionParsing(this);
    patternParser = new PatternParsing(this);
    myScopes = new ArrayDeque<>();
    myScopes.push(emptyParsingScope());
  }

  @NotNull
  public ParsingScope popScope() {
    final ParsingScope prevScope = myScopes.pop();
    resetBuilderCache(prevScope);
    return prevScope;
  }

  public void pushScope(@NotNull ParsingScope scope) {
    final ParsingScope prevScope = getScope();
    myScopes.push(scope);
    resetBuilderCache(prevScope);
  }

  @NotNull
  public ParsingScope getScope() {
    return myScopes.peek();
  }

  public StatementParsing getStatementParser() {
    return stmtParser;
  }

  public ExpressionParsing getExpressionParser() {
    return expressionParser;
  }

  public FunctionParsing getFunctionParser() {
    return functionParser;
  }

  public @NotNull PatternParsing getPatternParser() {
    return patternParser;
  }

  public SyntaxTreeBuilder getBuilder() {
    return myBuilder;
  }

  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public ParsingScope emptyParsingScope() {
    return new ParsingScope();
  }

  private void resetBuilderCache(@NotNull ParsingScope prevScope) {
    if (!getScope().equals(prevScope)) {
      // builder could cache some data based on the previous scope (e.g. token type)
      // and we have to reset its cache if scope has changed
      getBuilder().mark().rollbackTo();
    }
  }
}
