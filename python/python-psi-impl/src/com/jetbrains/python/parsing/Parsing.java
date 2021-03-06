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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts.ParsingError;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class Parsing {
  protected ParsingContext myContext;
  protected SyntaxTreeBuilder myBuilder;
  private static final Logger LOG = Logger.getInstance(Parsing.class);

  protected Parsing(ParsingContext context) {
    myContext = context;
    myBuilder = context.getBuilder();
  }

  public ParsingContext getParsingContext() {
    return myContext;
  }

  public ExpressionParsing getExpressionParser() {
    return getParsingContext().getExpressionParser();
  }

  public StatementParsing getStatementParser() {
    return getParsingContext().getStatementParser();
  }

  public FunctionParsing getFunctionParser() {
    return getParsingContext().getFunctionParser();
  }

  protected boolean checkMatches(final IElementType token, @NotNull @ParsingError String message) {
    if (myBuilder.getTokenType() == token) {
      myBuilder.advanceLexer();
      return true;
    }
    myBuilder.error(message);
    return false;
  }

  protected boolean parseIdentifierOrSkip(IElementType @NotNull ... validSuccessiveTokens) {
    if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
      myBuilder.advanceLexer();
      return true;
    }
    else {
      final SyntaxTreeBuilder.Marker nameExpected = myBuilder.mark();
      if (myBuilder.getTokenType() != PyTokenTypes.STATEMENT_BREAK && !atAnyOfTokens(validSuccessiveTokens)) {
        myBuilder.advanceLexer();
      }
      nameExpected.error(PyPsiBundle.message("PARSE.expected.identifier"));
      return false;
    }
  }

  protected void assertCurrentToken(final PyElementType tokenType) {
    LOG.assertTrue(myBuilder.getTokenType() == tokenType);
  }

  protected boolean atToken(@Nullable final IElementType tokenType) {
    return myBuilder.getTokenType() == tokenType;
  }

  protected boolean atToken(@NotNull final IElementType tokenType, @NotNull String tokenText) {
    return myBuilder.getTokenType() == tokenType && tokenText.equals(myBuilder.getTokenText());
  }

  protected boolean atAnyOfTokens(final IElementType... tokenTypes) {
    IElementType currentTokenType = myBuilder.getTokenType();
    for (IElementType tokenType : tokenTypes) {
      if (currentTokenType == tokenType) return true;
    }
    return false;
  }

  protected boolean atAnyOfTokens(@NotNull TokenSet tokenTypes) {
    return tokenTypes.contains(myBuilder.getTokenType());
  }

  protected boolean matchToken(final IElementType tokenType) {
    if (myBuilder.getTokenType() == tokenType) {
      myBuilder.advanceLexer();
      return true;
    }
    return false;
  }

  protected void nextToken() {
    myBuilder.advanceLexer();
  }

  protected void advanceAsync(boolean falseAsync) {
    if (falseAsync) {
      advanceError(myBuilder, PyPsiBundle.message("PARSE.async.keyword.not.expected.here"));
    }
    else {
      myBuilder.advanceLexer();
    }
  }

  protected static void advanceIdentifierLike(@NotNull SyntaxTreeBuilder builder) {
    if (isFalseIdentifier(builder)) {
      String tokenText = builder.getTokenText();
      advanceError(builder, PyPsiBundle.message("PARSE.keyword.cannot.be.used.as.identifier.py2", tokenText));
    }
    else {
      builder.advanceLexer();
    }
  }

  protected static void advanceError(@NotNull SyntaxTreeBuilder builder, @NotNull @ParsingError String message) {
    final SyntaxTreeBuilder.Marker err = builder.mark();
    builder.advanceLexer();
    err.error(message);
  }

  protected static boolean isIdentifier(@NotNull SyntaxTreeBuilder builder) {
    return builder.getTokenType() == PyTokenTypes.IDENTIFIER || isFalseIdentifier(builder);
  }

  private static boolean isFalseIdentifier(@NotNull SyntaxTreeBuilder builder) {
    return builder.getTokenType() == PyTokenTypes.EXEC_KEYWORD ||
           builder.getTokenType() == PyTokenTypes.PRINT_KEYWORD;
  }

  protected static void buildTokenElement(IElementType type, SyntaxTreeBuilder builder) {
    final SyntaxTreeBuilder.Marker marker = builder.mark();
    advanceIdentifierLike(builder);
    marker.done(type);
  }

  protected IElementType getReferenceType() {
    return PyElementTypes.REFERENCE_EXPRESSION;
  }
}
