package com.jetbrains.python.parsing;

import com.intellij.lang.PsiBuilder;
import com.jetbrains.python.psi.LanguageLevel;

public class ParsingContext {
  private final StatementParsing stmtParser;
  private final ExpressionParsing expressionParser;
  private final FunctionParsing functionParser;
  private final PsiBuilder myBuilder;
  private final LanguageLevel myLanguageLevel;

  public ParsingContext(final PsiBuilder builder, LanguageLevel languageLevel, StatementParsing.FUTURE futureFlag) {
    myBuilder = builder;
    myLanguageLevel = languageLevel;
    stmtParser = new StatementParsing(this, futureFlag);
    expressionParser = new ExpressionParsing(this);
    functionParser = new FunctionParsing(this);
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

  public PsiBuilder getBuilder() {
    return myBuilder;
  }

  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public ParsingScope emptyParsingScope() {
    return new ParsingScope();
  }
}
