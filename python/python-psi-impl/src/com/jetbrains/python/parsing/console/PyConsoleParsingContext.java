// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.parsing.console;

import com.intellij.lang.SyntaxTreeBuilder;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.parsing.ExpressionParsing;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.StatementParsing;
import com.jetbrains.python.psi.LanguageLevel;

public class PyConsoleParsingContext extends ParsingContext {
  private final StatementParsing stmtParser;
  private final ExpressionParsing expressionParser;

  public PyConsoleParsingContext(final SyntaxTreeBuilder builder,
                                 LanguageLevel languageLevel,
                                 PythonConsoleData pythonConsoleData,
                                 boolean startsWithIPythonSymbol) {
    super(builder, languageLevel);
    stmtParser = new ConsoleStatementParsing(this, startsWithIPythonSymbol, pythonConsoleData);
    if (pythonConsoleData.isIPythonEnabled()) {
      expressionParser = new ConsoleExpressionParsing(this);
    }
    else {
      expressionParser = new ExpressionParsing(this);
    }
  }

  @Override
  public StatementParsing getStatementParser() {
    return stmtParser;
  }

  @Override
  public ExpressionParsing getExpressionParser() {
    return expressionParser;
  }

  public static class ConsoleStatementParsing extends StatementParsing {

    private final boolean myStartsWithIPythonSymbol;
    private final PythonConsoleData myPythonConsoleData;

    protected ConsoleStatementParsing(ParsingContext context,
                                      boolean startsWithIPythonSymbol,
                                      PythonConsoleData pythonConsoleData) {
      super(context);
      myStartsWithIPythonSymbol = startsWithIPythonSymbol;
      myPythonConsoleData = pythonConsoleData;
    }


    @Override
    public void parseStatement() {
      if (parseIPythonHelp()) {
        return;
      }
      if (shouldParseIPythonCommand()) {
        parseIPythonCommand();
      }
      else {
        if (myPythonConsoleData.isIPythonEnabled()) {
          if (myPythonConsoleData.isIPythonAutomagic()) {
            if (myPythonConsoleData.isMagicCommand(myBuilder.getTokenText())) {
              parseIPythonCommand();
            }
          }
        }
        if (myPythonConsoleData.getIndentSize() > 0) {
          if (myBuilder.getTokenType() == PyTokenTypes.INDENT) {
            myBuilder.advanceLexer();
          }
        }
        super.parseStatement();
      }
    }

    private boolean parseIPythonHelp() {
      return parseIPythonGlobalHelp() ||
             parseIPythonSuffixHelp();
    }

    /**
     * Parse statements consisting of the single question mark.
     */
    private boolean parseIPythonGlobalHelp() {
      SyntaxTreeBuilder.Marker ipythonHelp = myBuilder.mark();
      if (myBuilder.getTokenType() == PyConsoleTokenTypes.QUESTION_MARK) {
        myBuilder.advanceLexer();
        if (myBuilder.getTokenType() == PyTokenTypes.STATEMENT_BREAK || myBuilder.eof()) {
          myBuilder.advanceLexer();
          ipythonHelp.done(PyElementTypes.EMPTY_EXPRESSION);
          return true;
        }
      }
      ipythonHelp.rollbackTo();
      return false;
    }

    /**
     * Parse statements ending with a question mark.
     */
    private boolean parseIPythonSuffixHelp() {
      SyntaxTreeBuilder.Marker ipythonHelp = myBuilder.mark();
      while (myBuilder.getTokenType() != PyTokenTypes.STATEMENT_BREAK &&
             myBuilder.getTokenType() != PyTokenTypes.LINE_BREAK &&
             !myBuilder.eof()
      ) {
        myBuilder.advanceLexer();
      }
      if (myBuilder.rawLookup(-1) != PyConsoleTokenTypes.QUESTION_MARK) {
        ipythonHelp.rollbackTo();
        return false;
      }
      int lookupIndex = -2;
      if (myBuilder.rawLookup(lookupIndex) == PyConsoleTokenTypes.QUESTION_MARK) {
        --lookupIndex;
      }
      if (myBuilder.rawLookup(lookupIndex) == PyTokenTypes.MULT) {
        ipythonHelp.rollbackTo();
        parseIPythonCommand();
        return true;
      }
      if (myBuilder.rawLookup(lookupIndex) != PyTokenTypes.IDENTIFIER) {
        myBuilder.error(PyPsiBundle.message("PARSE.console.help.request.warn"));
      }
      ipythonHelp.done(PyElementTypes.EMPTY_EXPRESSION);
      myBuilder.advanceLexer();
      return true;
    }

    protected boolean shouldParseIPythonCommand() {
      return myStartsWithIPythonSymbol;
    }

    protected boolean continueParseIPythonCommand() {
      return !myBuilder.eof();
    }

    private void parseIPythonCommand() {
      SyntaxTreeBuilder.Marker ipythonCommand = myBuilder.mark();
      while (continueParseIPythonCommand()) {
        myBuilder.advanceLexer();
      }
      ipythonCommand.done(PyElementTypes.EMPTY_EXPRESSION);
    }

  }

  public static class ConsoleExpressionParsing extends ExpressionParsing {
    public ConsoleExpressionParsing(ParsingContext context) {
      super(context);
    }

    @Override
    public boolean parseExpressionOptional() {
      if (myBuilder.getTokenType() == PyTokenTypes.PERC ||
          myBuilder.getTokenType() == PyConsoleTokenTypes.PLING ||
          myBuilder.getTokenType() == PyConsoleTokenTypes.QUESTION_MARK) {
        SyntaxTreeBuilder.Marker expr = myBuilder.mark();
        SyntaxTreeBuilder.Marker command = myBuilder.mark();

        myBuilder.advanceLexer();

        if (myBuilder.getTokenType() == PyConsoleTokenTypes.PLING) {
          myBuilder.advanceLexer();
        }

        if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
          myBuilder.advanceLexer();
          command.done(getReferenceType());
        }
        else {
          expr.drop();
          command.drop();
          myBuilder.error(PyPsiBundle.message("PARSE.expected.identifier"));
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

    @Override
    public boolean parseYieldOrTupleExpression(boolean isTargetExpression) {
      if (parseIPythonCaptureExpression()) {
        return true;
      }
      else {
        return super.parseYieldOrTupleExpression(isTargetExpression);
      }
    }

    private boolean parseIPythonCaptureExpression() {
      if (myBuilder.getTokenType() == PyConsoleTokenTypes.PLING) {
        captureIPythonExpression();
        return true;
      }
      if (myBuilder.getTokenType() == PyTokenTypes.PERC) {
        if (myBuilder.lookAhead(1) == PyTokenTypes.PERC) {
          myBuilder.error(PyPsiBundle.message("PARSE.console.multiline.magic.warn"));
        }
        captureIPythonExpression();
        return true;
      }
      return false;
    }

    private void captureIPythonExpression() {
      SyntaxTreeBuilder.Marker mark = myBuilder.mark();
      while (myBuilder.getTokenType() != PyTokenTypes.STATEMENT_BREAK) {
        myBuilder.advanceLexer();
      }
      mark.done(PyElementTypes.EMPTY_EXPRESSION);
    }
  }
}
