package com.jetbrains.python.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.psi.PyElementType;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class Parsing {
  protected ParsingContext myContext;
  protected PsiBuilder myBuilder;
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.parsing.Parsing");

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

  protected void checkMatches(final IElementType token, final String message) {
    if (myBuilder.getTokenType() == token) {
      myBuilder.advanceLexer();
    }
    else {
      myBuilder.error(message);
    }
  }

  protected boolean checkMatches(final TokenSet tokenSet, final String message) {
    if (tokenSet.contains(myBuilder.getTokenType())) {
      myBuilder.advanceLexer();
      return true;
    }
    myBuilder.error(message);
    return false;
  }

  protected void assertCurrentToken(final PyElementType tokenType) {
    LOG.assertTrue(myBuilder.getTokenType() == tokenType);
  }

  protected boolean atToken(@Nullable final IElementType tokenType) {
    return myBuilder.getTokenType() == tokenType;
  }

  protected boolean atAnyOfTokens(final IElementType... tokenTypes) {
    IElementType currentTokenType = myBuilder.getTokenType();
    for (IElementType tokenType : tokenTypes) {
      if (currentTokenType == tokenType) return true;
    }
    return false;
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
}
