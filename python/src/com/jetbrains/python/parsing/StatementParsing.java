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


  public void parseStatement(PsiBuilder builder) {
    while (builder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
      builder.advanceLexer();
    }

    final IElementType firstToken;
    firstToken = builder.getTokenType();

    if (firstToken == null) return;

    if (firstToken == PyTokenTypes.WHILE_KEYWORD) {
      parseWhileStatement(builder);
      return;
    }
    if (firstToken == PyTokenTypes.IF_KEYWORD) {
      parseIfStatement(builder);
      return;
    }
    if (firstToken == PyTokenTypes.FOR_KEYWORD) {
      parseForStatement(builder);
      return;
    }
    if (firstToken == PyTokenTypes.TRY_KEYWORD) {
      parseTryStatement(builder);
      return;
    }
    if (firstToken == PyTokenTypes.DEF_KEYWORD) {
      getFunctionParser().parseFunctionDeclaration(builder);
      return;
    }
    if (firstToken == PyTokenTypes.AT) {
      getFunctionParser().parseDecoratedFunctionDeclaration(builder);
      return;
    }
    if (firstToken == PyTokenTypes.CLASS_KEYWORD) {
      parseClassDeclaration(builder);
      return;
    }
    if (firstToken == PyTokenTypes.WITH_KEYWORD) {
      parseWithStatement(builder);
      return;
    }

    parseSimpleStatement(builder, false);
  }

  private void parseSimpleStatement(PsiBuilder builder, boolean inSuite) {
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
      parseImportStatement(builder, inSuite);
      return;
    }
    if (firstToken == PyTokenTypes.FROM_KEYWORD) {
      parseFromImportStatement(builder, inSuite);
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
      checkEndOfStatement(builder, inSuite);
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

      checkEndOfStatement(builder, inSuite);
      exprStatement.done(statementType);
      return;
    }
    else {
      exprStatement.drop();
    }

    builder.advanceLexer();
    builder.error("statement expected, found " + firstToken.toString());
  }

  private void checkEndOfStatement(PsiBuilder builder, boolean inSuite) {
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
      getExpressionParser().parseSingleExpression(builder, false);
      target.done(PyElementTypes.PRINT_TARGET);
    }
    else {
      getExpressionParser().parseSingleExpression(builder, false);
    }
    while (builder.getTokenType() == PyTokenTypes.COMMA) {
      builder.advanceLexer();
      if (PyTokenTypes.END_OF_STATEMENT.contains(builder.getTokenType())) {
        break;
      }
      getExpressionParser().parseSingleExpression(builder, false);
    }
    checkEndOfStatement(builder, inSuite);
    statement.done(PyElementTypes.PRINT_STATEMENT);
  }

  private void parseKeywordStatement(PsiBuilder builder, IElementType statementType, boolean inSuite) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    checkEndOfStatement(builder, inSuite);
    statement.done(statementType);
  }

  private void parseReturnStatement(PsiBuilder builder, boolean inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.RETURN_KEYWORD);
    final PsiBuilder.Marker returnStatement = builder.mark();
    builder.advanceLexer();
    if (!PyTokenTypes.END_OF_STATEMENT.contains(builder.getTokenType())) {
      getExpressionParser().parseExpression(builder);
    }
    checkEndOfStatement(builder, inSuite);
    returnStatement.done(PyElementTypes.RETURN_STATEMENT);
  }

  private void parseDelStatement(PsiBuilder builder, boolean inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.DEL_KEYWORD);
    final PsiBuilder.Marker delStatement = builder.mark();
    builder.advanceLexer();
    if (!getExpressionParser().parseSingleExpression(builder, false)) {
      builder.error("expression expected");
    }
    while (builder.getTokenType() == PyTokenTypes.COMMA) {
      builder.advanceLexer();
      if (!PyTokenTypes.END_OF_STATEMENT.contains(builder.getTokenType())) {
        if (!getExpressionParser().parseSingleExpression(builder, false)) {
          builder.error("expression expected");
        }
      }
    }

    checkEndOfStatement(builder, inSuite);
    delStatement.done(PyElementTypes.DEL_STATEMENT);
  }

  private void parseRaiseStatement(PsiBuilder builder, boolean inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.RAISE_KEYWORD);
    final PsiBuilder.Marker raiseStatement = builder.mark();
    builder.advanceLexer();
    if (!PyTokenTypes.END_OF_STATEMENT.contains(builder.getTokenType())) {
      getExpressionParser().parseSingleExpression(builder, false);
      if (builder.getTokenType() == PyTokenTypes.COMMA) {
        builder.advanceLexer();
        getExpressionParser().parseSingleExpression(builder, false);
        if (builder.getTokenType() == PyTokenTypes.COMMA) {
          builder.advanceLexer();
          getExpressionParser().parseSingleExpression(builder, false);
        }
      }
    }
    checkEndOfStatement(builder, inSuite);
    raiseStatement.done(PyElementTypes.RAISE_STATEMENT);
  }

  private void parseAssertStatement(PsiBuilder builder, boolean inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.ASSERT_KEYWORD);
    final PsiBuilder.Marker assertStatement = builder.mark();
    builder.advanceLexer();
    getExpressionParser().parseSingleExpression(builder, false);
    if (builder.getTokenType() == PyTokenTypes.COMMA) {
      builder.advanceLexer();
      getExpressionParser().parseSingleExpression(builder, false);
    }
    checkEndOfStatement(builder, inSuite);
    assertStatement.done(PyElementTypes.ASSERT_STATEMENT);
  }

  private void parseImportStatement(PsiBuilder builder, boolean inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.IMPORT_KEYWORD);
    final PsiBuilder.Marker importStatement = builder.mark();
    builder.advanceLexer();
    parseImportElements(builder, true, false);
    checkEndOfStatement(builder, inSuite);
    importStatement.done(PyElementTypes.IMPORT_STATEMENT);
  }

  private void parseFromImportStatement(PsiBuilder builder, boolean inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.FROM_KEYWORD);
    final PsiBuilder.Marker fromImportStatement = builder.mark();
    builder.advanceLexer();
    if (parseDottedName(builder)) {
      checkMatches(builder, PyTokenTypes.IMPORT_KEYWORD, "'import' expected");
      if (builder.getTokenType() == PyTokenTypes.MULT) {
        builder.advanceLexer();
      }
      else if (builder.getTokenType() == PyTokenTypes.LPAR) {
        builder.advanceLexer();
        parseImportElements(builder, false, true);
        checkMatches(builder, PyTokenTypes.RPAR, ") expected");
      }
      else {
        parseImportElements(builder, false, false);
      }
    }
    checkEndOfStatement(builder, inSuite);
    fromImportStatement.done(PyElementTypes.FROM_IMPORT_STATEMENT);
  }

  private void parseImportElements(PsiBuilder builder, boolean isModuleName, boolean inParens) {
    while (true) {
      final PsiBuilder.Marker asMarker = builder.mark();
      if (isModuleName) {
        if (!parseDottedName(builder)) {
          asMarker.drop();
          break;
        }
      }
      else {
        parseReferenceExpression(builder);
      }
      String tokenText = builder.getTokenText();
      if (builder.getTokenType() == PyTokenTypes.IDENTIFIER && tokenText != null && tokenText.equals("as")) {
        builder.advanceLexer();
        parseReferenceExpression(builder);
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

  private void parseReferenceExpression(PsiBuilder builder) {
    final PsiBuilder.Marker idMarker = builder.mark();
    if (builder.getTokenType() == PyTokenTypes.IDENTIFIER) {
      builder.advanceLexer();
      idMarker.done(PyElementTypes.REFERENCE_EXPRESSION);
    }
    else {
      builder.error("identifier expected");
      idMarker.drop();
    }
  }

  public boolean parseDottedName(PsiBuilder builder) {
    if (builder.getTokenType() != PyTokenTypes.IDENTIFIER) {
      builder.error("identifier expected");
      return false;
    }
    PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    marker.done(PyElementTypes.REFERENCE_EXPRESSION);
    while (builder.getTokenType() == PyTokenTypes.DOT) {
      marker = marker.precede();
      builder.advanceLexer();
      checkMatches(builder, PyTokenTypes.IDENTIFIER, "identifier expected");
      marker.done(PyElementTypes.REFERENCE_EXPRESSION);
    }
    return true;
  }

  private void parseGlobalStatement(PsiBuilder builder, boolean inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.GLOBAL_KEYWORD);
    final PsiBuilder.Marker globalStatement = builder.mark();
    builder.advanceLexer();
    parseReferenceExpression(builder);
    while (builder.getTokenType() == PyTokenTypes.COMMA) {
      builder.advanceLexer();
      parseReferenceExpression(builder);
    }
    checkEndOfStatement(builder, inSuite);
    globalStatement.done(PyElementTypes.GLOBAL_STATEMENT);
  }

  private void parseExecStatement(PsiBuilder builder, boolean inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.EXEC_KEYWORD);
    final PsiBuilder.Marker execStatement = builder.mark();
    builder.advanceLexer();
    getExpressionParser().parseExpression(builder, true, false);
    if (builder.getTokenType() == PyTokenTypes.IN_KEYWORD) {
      builder.advanceLexer();
      getExpressionParser().parseSingleExpression(builder, false);
      if (builder.getTokenType() == PyTokenTypes.COMMA) {
        builder.advanceLexer();
        getExpressionParser().parseSingleExpression(builder, false);
      }
    }
    checkEndOfStatement(builder, inSuite);
    execStatement.done(PyElementTypes.EXEC_STATEMENT);
  }

  private void parseIfStatement(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.IF_KEYWORD);
    final PsiBuilder.Marker ifStatement = builder.mark();
    builder.advanceLexer();
    getExpressionParser().parseExpression(builder);
    checkMatches(builder, PyTokenTypes.COLON, "colon expected");
    parseSuite(builder);
    while (builder.getTokenType() == PyTokenTypes.ELIF_KEYWORD) {
      builder.advanceLexer();
      getExpressionParser().parseExpression(builder);
      checkMatches(builder, PyTokenTypes.COLON, "colon expected");
      parseSuite(builder);
    }
    if (builder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
      builder.advanceLexer();
      checkMatches(builder, PyTokenTypes.COLON, "colon expected");
      parseSuite(builder);
    }
    ifStatement.done(PyElementTypes.IF_STATEMENT);
  }

  private void parseForStatement(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.FOR_KEYWORD);
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    getExpressionParser().parseExpression(builder, true, true);
    checkMatches(builder, PyTokenTypes.IN_KEYWORD, "'in' expected");
    getExpressionParser().parseExpression(builder);
    checkMatches(builder, PyTokenTypes.COLON, "colon expected");
    parseSuite(builder);
    if (builder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
      builder.advanceLexer();
      checkMatches(builder, PyTokenTypes.COLON, "colon expected");
      parseSuite(builder);
    }
    statement.done(PyElementTypes.FOR_STATEMENT);
  }

  private void parseWhileStatement(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.WHILE_KEYWORD);
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    getExpressionParser().parseExpression(builder);
    checkMatches(builder, PyTokenTypes.COLON, "colon expected");
    parseSuite(builder);
    if (builder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
      builder.advanceLexer();
      checkMatches(builder, PyTokenTypes.COLON, "colon expected");
      parseSuite(builder);
    }
    statement.done(PyElementTypes.WHILE_STATEMENT);
  }

  private void parseTryStatement(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.TRY_KEYWORD);
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    checkMatches(builder, PyTokenTypes.COLON, "colon expected");
    parseSuite(builder);
    boolean haveExceptClause = false;
    if (builder.getTokenType() == PyTokenTypes.EXCEPT_KEYWORD) {
      haveExceptClause = true;
      while (builder.getTokenType() == PyTokenTypes.EXCEPT_KEYWORD) {
        final PsiBuilder.Marker exceptBlock = builder.mark();
        builder.advanceLexer();
        if (builder.getTokenType() != PyTokenTypes.COLON) {
          if (!getExpressionParser().parseSingleExpression(builder, false)) {
            builder.error("expression expected");
          }
          if (builder.getTokenType() == PyTokenTypes.COMMA) {
            builder.advanceLexer();
            if (!getExpressionParser().parseSingleExpression(builder, true)) {
              builder.error("expression expected");
            }
          }
        }
        checkMatches(builder, PyTokenTypes.COLON, "colon expected");
        parseSuite(builder);
        exceptBlock.done(PyElementTypes.EXCEPT_BLOCK);
      }
      if (builder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
        builder.advanceLexer();
        checkMatches(builder, PyTokenTypes.COLON, "colon expected");
        parseSuite(builder);
      }
    }
    if (builder.getTokenType() == PyTokenTypes.FINALLY_KEYWORD) {
      builder.advanceLexer();
      checkMatches(builder, PyTokenTypes.COLON, "colon expected");
      parseSuite(builder);
    }
    else if (!haveExceptClause) {
      builder.error("'except' or 'finally' expected");
      // much better to have a statement of incorrectly determined type
      // than "TRY" and "COLON" tokens attached to nothing
    }
    statement.done(PyElementTypes.TRY_EXCEPT_STATEMENT);
  }

  private void parseWithStatement(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.WITH_KEYWORD);
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    getExpressionParser().parseExpression(builder);
    if (builder.getTokenType() == PyTokenTypes.AS_KEYWORD) {
      builder.advanceLexer();
      getExpressionParser().parseExpression(builder);
    }
    checkMatches(builder, PyTokenTypes.COLON, "colon expected");
    parseSuite(builder);
    statement.done(PyElementTypes.WITH_STATEMENT);
  }

  private void parseClassDeclaration(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.CLASS_KEYWORD);
    final PsiBuilder.Marker classMarker = builder.mark();
    builder.advanceLexer();
    checkMatches(builder, PyTokenTypes.IDENTIFIER, "identifier expected");
    final PsiBuilder.Marker inheritMarker = builder.mark();
    if (builder.getTokenType() == PyTokenTypes.LPAR) {
      builder.advanceLexer();
      getExpressionParser().parseExpression(builder);
      checkMatches(builder, PyTokenTypes.RPAR, ") expected");
    }
    inheritMarker.done(PyElementTypes.PARENTHESIZED_EXPRESSION);
    checkMatches(builder, PyTokenTypes.COLON, "colon expected");
    parseSuite(builder);
    classMarker.done(PyElementTypes.CLASS_DECLARATION);
  }

  public void parseSuite(PsiBuilder builder) {
    parseSuite(builder, null, null);
  }

  public void parseSuite(PsiBuilder builder, PsiBuilder.Marker endMarker, IElementType elType) {
    if (builder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
      builder.advanceLexer();

      final PsiBuilder.Marker marker = builder.mark();
      if (builder.getTokenType() != PyTokenTypes.INDENT) {
        builder.error("indent expected");
      }
      else {
        builder.advanceLexer();
        while (!builder.eof() && builder.getTokenType() != PyTokenTypes.DEDENT) {
          parseStatement(builder);
        }
      }

      marker.done(PyElementTypes.STATEMENT_LIST);
      if (endMarker != null) {
        endMarker.done(elType);
      }
      if (!builder.eof()) {
        checkMatches(builder, PyTokenTypes.DEDENT, "dedent expected");
      }
      // HACK: the following line advances the PsiBuilder lexer and thus
      // ensures that the whitespace following the statement list is included
      // in the block containing the statement list
      builder.getTokenType();
    }
    else {
      final PsiBuilder.Marker marker = builder.mark();
      parseSimpleStatement(builder, true);
      while (builder.getTokenType() == PyTokenTypes.SEMICOLON) {
        builder.advanceLexer();
        parseSimpleStatement(builder, true);
      }
      marker.done(PyElementTypes.STATEMENT_LIST);
      if (endMarker != null) {
        endMarker.done(elType);
      }
    }
  }
}
