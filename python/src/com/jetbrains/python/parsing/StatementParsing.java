/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;

/**
 * @author yole
 */
public class StatementParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.parsing.StatementParsing");

  protected StatementParsing(ParsingContext context) {
    super(context);
  }


  public void parseStatement() {
    while (myBuilder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
      myBuilder.advanceLexer();
    }

    final IElementType firstToken;
    firstToken = myBuilder.getTokenType();

    if (firstToken == null) return;

    if (firstToken == PyTokenTypes.WHILE_KEYWORD) {
      parseWhileStatement(myBuilder);
      return;
    }
    if (firstToken == PyTokenTypes.IF_KEYWORD) {
      parseIfStatement(myBuilder);
      return;
    }
    if (firstToken == PyTokenTypes.FOR_KEYWORD) {
      parseForStatement(myBuilder);
      return;
    }
    if (firstToken == PyTokenTypes.TRY_KEYWORD) {
      parseTryStatement(myBuilder);
      return;
    }
    if (firstToken == PyTokenTypes.DEF_KEYWORD) {
      getFunctionParser().parseFunctionDeclaration();
      return;
    }
    if (firstToken == PyTokenTypes.AT) {
      getFunctionParser().parseDecoratedFunctionDeclaration();
      return;
    }
    if (firstToken == PyTokenTypes.CLASS_KEYWORD) {
      parseClassDeclaration();
      return;
    }
    if (firstToken == PyTokenTypes.WITH_KEYWORD) {
      parseWithStatement();
      return;
    }

    parseSimpleStatement(false);
  }

  private void parseSimpleStatement(boolean inSuite) {
    PsiBuilder builder = myContext.getBuilder();
    final IElementType firstToken = builder.getTokenType();
    if (firstToken == null) {
      return;
    }
    if (firstToken == PyTokenTypes.PRINT_KEYWORD) {
      parsePrintStatement(builder, inSuite);
      return;
    }
    if (firstToken == PyTokenTypes.ASSERT_KEYWORD) {
      parseAssertStatement(builder, inSuite);
      return;
    }
    if (firstToken == PyTokenTypes.BREAK_KEYWORD) {
      parseKeywordStatement(builder, PyElementTypes.BREAK_STATEMENT, inSuite);
      return;
    }
    if (firstToken == PyTokenTypes.CONTINUE_KEYWORD) {
      parseKeywordStatement(builder, PyElementTypes.CONTINUE_STATEMENT, inSuite);
      return;
    }
    if (firstToken == PyTokenTypes.DEL_KEYWORD) {
      parseDelStatement(builder, inSuite);
      return;
    }
    if (firstToken == PyTokenTypes.EXEC_KEYWORD) {
      parseExecStatement(builder, inSuite);
      return;
    }
    if (firstToken == PyTokenTypes.GLOBAL_KEYWORD) {
      parseGlobalStatement(builder, inSuite);
      return;
    }
    if (firstToken == PyTokenTypes.IMPORT_KEYWORD) {
      parseImportStatement(inSuite);
      return;
    }
    if (firstToken == PyTokenTypes.FROM_KEYWORD) {
      parseFromImportStatement(inSuite);
      return;
    }
    if (firstToken == PyTokenTypes.PASS_KEYWORD) {
      parseKeywordStatement(builder, PyElementTypes.PASS_STATEMENT, inSuite);
      return;
    }
    if (firstToken == PyTokenTypes.RETURN_KEYWORD) {
      parseReturnStatement(builder, inSuite);
      return;
    }
    if (firstToken == PyTokenTypes.RAISE_KEYWORD) {
      parseRaiseStatement(builder, inSuite);
      return;
    }
    PsiBuilder.Marker exprStatement = builder.mark();
    if (builder.getTokenType() == PyTokenTypes.YIELD_KEYWORD) {
      getExpressionParser().parseYieldOrTupleExpression(builder, false);
      checkEndOfStatement(inSuite);
      exprStatement.done(PyElementTypes.EXPRESSION_STATEMENT);
      return;
    }
    else if (getExpressionParser().parseExpressionOptional(builder)) {
      IElementType statementType = PyElementTypes.EXPRESSION_STATEMENT;
      if (PyTokenTypes.AUG_ASSIGN_OPERATIONS.contains(builder.getTokenType())) {
        statementType = PyElementTypes.AUG_ASSIGNMENT_STATEMENT;
        builder.advanceLexer();
        if (!getExpressionParser().parseYieldOrTupleExpression(builder, false)) {
          builder.error("expression expected");          
        }
      }
      else if (builder.getTokenType() == PyTokenTypes.EQ) {
        statementType = PyElementTypes.ASSIGNMENT_STATEMENT;
        exprStatement.rollbackTo();
        exprStatement = builder.mark();
        getExpressionParser().parseExpression(builder, false, true);
        LOG.assertTrue(builder.getTokenType() == PyTokenTypes.EQ);
        builder.advanceLexer();

        while (true) {
          PsiBuilder.Marker maybeExprMarker = builder.mark();
          if (!getExpressionParser().parseYieldOrTupleExpression(builder, false)) {
            maybeExprMarker.drop();
            builder.error("expression expected");
            break;
          }
          if (builder.getTokenType() == PyTokenTypes.EQ) {
            maybeExprMarker.rollbackTo();
            getExpressionParser().parseExpression(builder, false, true);
            LOG.assertTrue(builder.getTokenType() == PyTokenTypes.EQ);
            builder.advanceLexer();
          }
          else {
            maybeExprMarker.drop();
            break;
          }
        }
      }

      checkEndOfStatement(inSuite);
      exprStatement.done(statementType);
      return;
    }
    else {
      exprStatement.drop();
    }

    builder.advanceLexer();
    builder.error("statement expected, found " + firstToken.toString());
  }

  private void checkEndOfStatement(boolean inSuite) {
    PsiBuilder builder = myContext.getBuilder();
    if (builder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
      builder.advanceLexer();
    }
    else if (builder.getTokenType() == PyTokenTypes.SEMICOLON) {
      if (!inSuite) {
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
      builder.error("end of statement expected");
    }
  }

  private void parsePrintStatement(final PsiBuilder builder, boolean inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.PRINT_KEYWORD);
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() == PyTokenTypes.GTGT) {
      final PsiBuilder.Marker target = builder.mark();
      builder.advanceLexer();
      getExpressionParser().parseSingleExpression(false);
      target.done(PyElementTypes.PRINT_TARGET);
    }
    else {
      getExpressionParser().parseSingleExpression(false);
    }
    while (builder.getTokenType() == PyTokenTypes.COMMA) {
      builder.advanceLexer();
      if (PyTokenTypes.END_OF_STATEMENT.contains(builder.getTokenType())) {
        break;
      }
      getExpressionParser().parseSingleExpression(false);
    }
    checkEndOfStatement(inSuite);
    statement.done(PyElementTypes.PRINT_STATEMENT);
  }

  private void parseKeywordStatement(PsiBuilder builder, IElementType statementType, boolean inSuite) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    checkEndOfStatement(inSuite);
    statement.done(statementType);
  }

  private void parseReturnStatement(PsiBuilder builder, boolean inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.RETURN_KEYWORD);
    final PsiBuilder.Marker returnStatement = builder.mark();
    builder.advanceLexer();
    if (!PyTokenTypes.END_OF_STATEMENT.contains(builder.getTokenType())) {
      getExpressionParser().parseExpression();
    }
    checkEndOfStatement(inSuite);
    returnStatement.done(PyElementTypes.RETURN_STATEMENT);
  }

  private void parseDelStatement(PsiBuilder builder, boolean inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.DEL_KEYWORD);
    final PsiBuilder.Marker delStatement = builder.mark();
    builder.advanceLexer();
    if (!getExpressionParser().parseSingleExpression(false)) {
      builder.error("expression expected");
    }
    while (builder.getTokenType() == PyTokenTypes.COMMA) {
      builder.advanceLexer();
      if (!PyTokenTypes.END_OF_STATEMENT.contains(builder.getTokenType())) {
        if (!getExpressionParser().parseSingleExpression(false)) {
          builder.error("expression expected");
        }
      }
    }

    checkEndOfStatement(inSuite);
    delStatement.done(PyElementTypes.DEL_STATEMENT);
  }

  private void parseRaiseStatement(PsiBuilder builder, boolean inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.RAISE_KEYWORD);
    final PsiBuilder.Marker raiseStatement = builder.mark();
    builder.advanceLexer();
    if (!PyTokenTypes.END_OF_STATEMENT.contains(builder.getTokenType())) {
      getExpressionParser().parseSingleExpression(false);
      if (builder.getTokenType() == PyTokenTypes.COMMA) {
        builder.advanceLexer();
        getExpressionParser().parseSingleExpression(false);
        if (builder.getTokenType() == PyTokenTypes.COMMA) {
          builder.advanceLexer();
          getExpressionParser().parseSingleExpression(false);
        }
      }
    }
    checkEndOfStatement(inSuite);
    raiseStatement.done(PyElementTypes.RAISE_STATEMENT);
  }

  private void parseAssertStatement(PsiBuilder builder, boolean inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.ASSERT_KEYWORD);
    final PsiBuilder.Marker assertStatement = builder.mark();
    builder.advanceLexer();
    getExpressionParser().parseSingleExpression(false);
    if (builder.getTokenType() == PyTokenTypes.COMMA) {
      builder.advanceLexer();
      getExpressionParser().parseSingleExpression(false);
    }
    checkEndOfStatement(inSuite);
    assertStatement.done(PyElementTypes.ASSERT_STATEMENT);
  }

  private void parseImportStatement(boolean inSuite) {
    PsiBuilder builder = myContext.getBuilder();
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.IMPORT_KEYWORD);
    final PsiBuilder.Marker importStatement = builder.mark();
    builder.advanceLexer();
    parseImportElements(true, false);
    checkEndOfStatement(inSuite);
    importStatement.done(PyElementTypes.IMPORT_STATEMENT);
  }

  private void parseFromImportStatement(boolean inSuite) {
    PsiBuilder builder = myContext.getBuilder();
    assertCurrentToken(PyTokenTypes.FROM_KEYWORD);
    final PsiBuilder.Marker fromImportStatement = builder.mark();
    builder.advanceLexer();
    if (parseDottedName()) {
      checkMatches(PyTokenTypes.IMPORT_KEYWORD, "'import' expected");
      if (builder.getTokenType() == PyTokenTypes.MULT) {
        builder.advanceLexer();
      }
      else if (builder.getTokenType() == PyTokenTypes.LPAR) {
        builder.advanceLexer();
        parseImportElements(false, true);
        checkMatches(PyTokenTypes.RPAR, ") expected");
      }
      else {
        parseImportElements(false, false);
      }
    }
    checkEndOfStatement(inSuite);
    fromImportStatement.done(PyElementTypes.FROM_IMPORT_STATEMENT);
  }

  private void parseImportElements(boolean isModuleName, boolean inParens) {
    PsiBuilder builder = myContext.getBuilder();
    while (true) {
      final PsiBuilder.Marker asMarker = builder.mark();
      if (isModuleName) {
        if (!parseDottedName()) {
          asMarker.drop();
          break;
        }
      }
      else {
        parseIdentifier(PyElementTypes.REFERENCE_EXPRESSION);
      }
      String tokenText = builder.getTokenText();
      if (builder.getTokenType() == PyTokenTypes.IDENTIFIER && tokenText != null && tokenText.equals("as")) {
        builder.advanceLexer();
        parseIdentifier(PyElementTypes.TARGET_EXPRESSION);
      }
      asMarker.done(PyElementTypes.IMPORT_ELEMENT);
      if (builder.getTokenType() == PyTokenTypes.COMMA) {
        builder.advanceLexer();
        if (inParens && builder.getTokenType() == PyTokenTypes.RPAR) {
          break;
        }
      }
      else {
        break;
      }
    }
  }

  private void parseIdentifier(final IElementType elementType) {
    final PsiBuilder.Marker idMarker = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
      myBuilder.advanceLexer();
      idMarker.done(elementType);
    }
    else {
      myBuilder.error("identifier expected");
      idMarker.drop();
    }
  }

  public boolean parseDottedName() {
    if (myBuilder.getTokenType() != PyTokenTypes.IDENTIFIER) {
      myBuilder.error("identifier expected");
      return false;
    }
    PsiBuilder.Marker marker = myBuilder.mark();
    myBuilder.advanceLexer();
    marker.done(PyElementTypes.REFERENCE_EXPRESSION);
    while (myBuilder.getTokenType() == PyTokenTypes.DOT) {
      marker = marker.precede();
      myBuilder.advanceLexer();
      checkMatches(PyTokenTypes.IDENTIFIER, "identifier expected");
      marker.done(PyElementTypes.REFERENCE_EXPRESSION);
    }
    return true;
  }

  private void parseGlobalStatement(PsiBuilder builder, boolean inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.GLOBAL_KEYWORD);
    final PsiBuilder.Marker globalStatement = builder.mark();
    builder.advanceLexer();
    parseIdentifier(PyElementTypes.REFERENCE_EXPRESSION);
    while (builder.getTokenType() == PyTokenTypes.COMMA) {
      builder.advanceLexer();
      parseIdentifier(PyElementTypes.REFERENCE_EXPRESSION);
    }
    checkEndOfStatement(inSuite);
    globalStatement.done(PyElementTypes.GLOBAL_STATEMENT);
  }

  private void parseExecStatement(PsiBuilder builder, boolean inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.EXEC_KEYWORD);
    final PsiBuilder.Marker execStatement = builder.mark();
    builder.advanceLexer();
    getExpressionParser().parseExpression(builder, true, false);
    if (builder.getTokenType() == PyTokenTypes.IN_KEYWORD) {
      builder.advanceLexer();
      getExpressionParser().parseSingleExpression(false);
      if (builder.getTokenType() == PyTokenTypes.COMMA) {
        builder.advanceLexer();
        getExpressionParser().parseSingleExpression(false);
      }
    }
    checkEndOfStatement(inSuite);
    execStatement.done(PyElementTypes.EXEC_STATEMENT);
  }

  private void parseIfStatement(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.IF_KEYWORD);
    final PsiBuilder.Marker ifStatement = builder.mark();
    builder.advanceLexer();
    getExpressionParser().parseExpression();
    checkMatches(PyTokenTypes.COLON, "colon expected");
    parseSuite();
    while (builder.getTokenType() == PyTokenTypes.ELIF_KEYWORD) {
      builder.advanceLexer();
      getExpressionParser().parseExpression();
      checkMatches(PyTokenTypes.COLON, "colon expected");
      parseSuite();
    }
    if (builder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
      builder.advanceLexer();
      checkMatches(PyTokenTypes.COLON, "colon expected");
      parseSuite();
    }
    ifStatement.done(PyElementTypes.IF_STATEMENT);
  }

  private void parseForStatement(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.FOR_KEYWORD);
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    getExpressionParser().parseExpression(builder, true, true);
    checkMatches(PyTokenTypes.IN_KEYWORD, "'in' expected");
    getExpressionParser().parseExpression();
    checkMatches(PyTokenTypes.COLON, "colon expected");
    parseSuite();
    if (builder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
      builder.advanceLexer();
      checkMatches(PyTokenTypes.COLON, "colon expected");
      parseSuite();
    }
    statement.done(PyElementTypes.FOR_STATEMENT);
  }

  private void parseWhileStatement(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.WHILE_KEYWORD);
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    getExpressionParser().parseExpression();
    checkMatches(PyTokenTypes.COLON, "colon expected");
    parseSuite();
    if (builder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
      builder.advanceLexer();
      checkMatches(PyTokenTypes.COLON, "colon expected");
      parseSuite();
    }
    statement.done(PyElementTypes.WHILE_STATEMENT);
  }

  private void parseTryStatement(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.TRY_KEYWORD);
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    checkMatches(PyTokenTypes.COLON, "colon expected");
    parseSuite();
    boolean haveExceptClause = false;
    if (builder.getTokenType() == PyTokenTypes.EXCEPT_KEYWORD) {
      haveExceptClause = true;
      while (builder.getTokenType() == PyTokenTypes.EXCEPT_KEYWORD) {
        final PsiBuilder.Marker exceptBlock = builder.mark();
        builder.advanceLexer();
        if (builder.getTokenType() != PyTokenTypes.COLON) {
          if (!getExpressionParser().parseSingleExpression(false)) {
            builder.error("expression expected");
          }
          if (builder.getTokenType() == PyTokenTypes.COMMA) {
            builder.advanceLexer();
            if (!getExpressionParser().parseSingleExpression(true)) {
              builder.error("expression expected");
            }
          }
        }
        checkMatches(PyTokenTypes.COLON, "colon expected");
        parseSuite();
        exceptBlock.done(PyElementTypes.EXCEPT_BLOCK);
      }
      if (builder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
        builder.advanceLexer();
        checkMatches(PyTokenTypes.COLON, "colon expected");
        parseSuite();
      }
    }
    if (builder.getTokenType() == PyTokenTypes.FINALLY_KEYWORD) {
      builder.advanceLexer();
      checkMatches(PyTokenTypes.COLON, "colon expected");
      parseSuite();
    }
    else if (!haveExceptClause) {
      builder.error("'except' or 'finally' expected");
      // much better to have a statement of incorrectly determined type
      // than "TRY" and "COLON" tokens attached to nothing
    }
    statement.done(PyElementTypes.TRY_EXCEPT_STATEMENT);
  }

  private void parseWithStatement() {
    assertCurrentToken(PyTokenTypes.WITH_KEYWORD);
    final PsiBuilder.Marker statement = myBuilder.mark();
    myBuilder.advanceLexer();
    getExpressionParser().parseExpression();
    if (myBuilder.getTokenType() == PyTokenTypes.AS_KEYWORD) {
      myBuilder.advanceLexer();
      getExpressionParser().parseExpression();
    }
    checkMatches(PyTokenTypes.COLON, "colon expected");
    parseSuite();
    statement.done(PyElementTypes.WITH_STATEMENT);
  }

  private void parseClassDeclaration() {
    assertCurrentToken(PyTokenTypes.CLASS_KEYWORD);
    final PsiBuilder.Marker classMarker = myBuilder.mark();
    myBuilder.advanceLexer();
    checkMatches(PyTokenTypes.IDENTIFIER, "identifier expected");
    final PsiBuilder.Marker inheritMarker = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.LPAR) {
      myBuilder.advanceLexer();
      getExpressionParser().parseExpression();
      checkMatches(PyTokenTypes.RPAR, ") expected");
    }
    inheritMarker.done(PyElementTypes.PARENTHESIZED_EXPRESSION);
    checkMatches(PyTokenTypes.COLON, "colon expected");
    parseSuite();
    classMarker.done(PyElementTypes.CLASS_DECLARATION);
  }

  public void parseSuite() {
    parseSuite(null, null);
  }

  public void parseSuite(PsiBuilder.Marker endMarker, IElementType elType) {
    if (myBuilder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
      myBuilder.advanceLexer();

      final PsiBuilder.Marker marker = myBuilder.mark();
      if (myBuilder.getTokenType() != PyTokenTypes.INDENT) {
        myBuilder.error("indent expected");
      }
      else {
        myBuilder.advanceLexer();
        while (!myBuilder.eof() && myBuilder.getTokenType() != PyTokenTypes.DEDENT) {
          parseStatement();
        }
      }

      marker.done(PyElementTypes.STATEMENT_LIST);
      if (endMarker != null) {
        endMarker.done(elType);
      }
      if (!myBuilder.eof()) {
        checkMatches(PyTokenTypes.DEDENT, "dedent expected");
      }
      // HACK: the following line advances the PsiBuilder lexer and thus
      // ensures that the whitespace following the statement list is included
      // in the block containing the statement list
      myBuilder.getTokenType();
    }
    else {
      final PsiBuilder.Marker marker = myBuilder.mark();
      parseSimpleStatement(true);
      while (myBuilder.getTokenType() == PyTokenTypes.SEMICOLON) {
        myBuilder.advanceLexer();
        parseSimpleStatement(true);
      }
      marker.done(PyElementTypes.STATEMENT_LIST);
      if (endMarker != null) {
        endMarker.done(elType);
      }
    }
  }
}
