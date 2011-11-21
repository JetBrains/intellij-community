package com.jetbrains.python.console.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.parsing.ExpressionParsing;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.ParsingScope;
import com.jetbrains.python.parsing.StatementParsing;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class PyConsoleParsingContext extends ParsingContext {
  private final StatementParsing stmtParser;
  private final ExpressionParsing expressionParser;
  private boolean myStartsWithIPythonSymbol;

  public PyConsoleParsingContext(final PsiBuilder builder,
                                 LanguageLevel languageLevel,
                                 StatementParsing.FUTURE futureFlag,
                                 PsiElement psi, boolean startsWithIPythonSymbol) {
    super(builder, languageLevel, futureFlag);
    myStartsWithIPythonSymbol = startsWithIPythonSymbol;
    stmtParser = new ConsoleStatementParsing(this, futureFlag, myStartsWithIPythonSymbol);
    expressionParser = new ConsoleExpressionParsing(this);
  }

  @Override
  public StatementParsing getStatementParser() {
    return stmtParser;
  }

  @Override
  public ExpressionParsing getExpressionParser() {
    return expressionParser;
  }

  private static class ConsoleStatementParsing extends StatementParsing {

    private boolean myStartsWithIPythonSymbol;

    protected ConsoleStatementParsing(ParsingContext context, @Nullable FUTURE futureFlag, boolean startsWithIPythonSymbol) {
      super(context, futureFlag);
      myStartsWithIPythonSymbol = startsWithIPythonSymbol;
    }


    @Override
    public void parseStatement(ParsingScope scope) {
      if (myStartsWithIPythonSymbol) {
        PsiBuilder.Marker ipythonCommend = myBuilder.mark();
        while (!myBuilder.eof()) {
          myBuilder.advanceLexer();
        }
        ipythonCommend.done(PyElementTypes.EMPTY_EXPRESSION);
      }
      else {
        super.parseStatement(scope);
      }
    }

    protected void checkEndOfStatement(ParsingScope scope) {
      PsiBuilder builder = myContext.getBuilder();
      if (builder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
        builder.advanceLexer();
      }
      else if (builder.getTokenType() == PyTokenTypes.SEMICOLON) {
        if (!scope.isSuite()) {
          builder.advanceLexer();
          if (builder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
            builder.advanceLexer();
          }
        }
      }
      else if (builder.eof()) {
        return;
      }
      else {
        if (builder.getTokenType() == PyConsoleTokenTypes.PLING || builder.getTokenType() == PyConsoleTokenTypes.QUESTION_MARK) {
          builder.advanceLexer();
          if (builder.getTokenType() == PyConsoleTokenTypes.PLING || builder.getTokenType() == PyConsoleTokenTypes.QUESTION_MARK) {
            builder.advanceLexer();
          }

          return;
        }
        builder.error("end of statement expected");
      }
    }
  }

  private static class ConsoleExpressionParsing extends ExpressionParsing {
    public ConsoleExpressionParsing(ParsingContext context) {
      super(context);
    }

    @Override
    public boolean parseExpressionOptional() {
      if (myBuilder.getTokenType() == PyTokenTypes.PERC ||
          myBuilder.getTokenType() == PyConsoleTokenTypes.PLING ||
          myBuilder.getTokenType() == PyConsoleTokenTypes.QUESTION_MARK) {
        PsiBuilder.Marker expr = myBuilder.mark();
        PsiBuilder.Marker command = myBuilder.mark();

        myBuilder.advanceLexer();

        if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
          myBuilder.advanceLexer();
          command.done(PyElementTypes.REFERENCE_EXPRESSION);
        }
        else {
          expr.drop();
          command.drop();
          myBuilder.error("Identifier expected.");
          return false;
        }
        while (myBuilder.getTokenType() != null) {
          myBuilder.advanceLexer();
        }
        expr.done(PyElementTypes.EMPTY_EXPRESSION);
        return true;
      }
      else {
        return super.parseExpressionOptional();
      }
    }
  }
}
