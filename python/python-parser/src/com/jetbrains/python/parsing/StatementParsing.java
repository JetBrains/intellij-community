// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing;

import com.intellij.lang.ITokenTypeRemapper;
import com.intellij.lang.SyntaxTreeBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyParsingBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;


public class StatementParsing extends Parsing implements ITokenTypeRemapper {
  private static final Logger LOG = Logger.getInstance(StatementParsing.class);
  protected static final @NonNls String TOK_FUTURE_IMPORT = PyNames.FUTURE_MODULE;
  protected static final @NonNls String TOK_PRINT_FUNCTION = "print_function";
  protected static final @NonNls String TOK_WITH = PyNames.WITH;
  protected static final @NonNls String TOK_AS = PyNames.AS;
  protected static final @NonNls String TOK_PRINT = PyNames.PRINT;
  protected static final @NonNls String TOK_NONE = PyNames.NONE;
  protected static final @NonNls String TOK_TRUE = PyNames.TRUE;
  protected static final @NonNls String TOK_DEBUG = PyNames.DEBUG;
  protected static final @NonNls String TOK_FALSE = PyNames.FALSE;
  protected static final @NonNls String TOK_NONLOCAL = PyNames.NONLOCAL;
  protected static final @NonNls String TOK_EXEC = PyNames.EXEC;
  public static final @NonNls String TOK_ASYNC = PyNames.ASYNC;
  protected static final @NonNls String TOK_AWAIT = PyNames.AWAIT;
  protected static final @NonNls String TOK_OBJECT = PyNames.OBJECT;
  protected static final @NonNls String TOK_TYPE = PyNames.TYPE;
  protected static final @NonNls String TOK_IMPORT = PyNames.IMPORT;
  protected static final @NonNls String TOK_IN = PyNames.IN;
  protected static final @NonNls String TOK_FROM = PyNames.FROM;
  protected static final @NonNls String TOK_MATCH = PyNames.MATCH;
  protected static final @NonNls String TOK_CASE = PyNames.CASE;

  protected enum Phase {NONE, FROM, FUTURE, IMPORT} // 'from __future__ import' phase

  private Phase myFutureImportPhase = Phase.NONE;

  protected Set<FutureFeature> myFutureFlags = EnumSet.noneOf(FutureFeature.class);

  public static class ImportTypes {
    public final IElementType statement;
    public final IElementType element;
    public IElementType starElement;

    public ImportTypes(IElementType statement, IElementType element, IElementType starElement) {
      this.statement = statement;
      this.element = element;
      this.starElement = starElement;
    }
  }

  public StatementParsing(ParsingContext context) {
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
      parseWhileStatement();
      return;
    }
    if (firstToken == PyTokenTypes.IF_KEYWORD) {
      parseIfStatement(PyTokenTypes.IF_KEYWORD, PyTokenTypes.ELIF_KEYWORD, PyTokenTypes.ELSE_KEYWORD, PyElementTypes.IF_STATEMENT);
      return;
    }
    if (firstToken == PyTokenTypes.FOR_KEYWORD) {
      parseForStatement(myBuilder.mark());
      return;
    }
    if (firstToken == PyTokenTypes.TRY_KEYWORD) {
      parseTryStatement();
      return;
    }
    if (firstToken == PyTokenTypes.DEF_KEYWORD) {
      getFunctionParser().parseFunctionDeclaration(myBuilder.mark(), false);
      return;
    }
    if (firstToken == PyTokenTypes.AT) {
      getFunctionParser().parseDecoratedDeclaration();
      return;
    }
    if (firstToken == PyTokenTypes.CLASS_KEYWORD) {
      parseClassDeclaration();
      return;
    }
    if (firstToken == PyTokenTypes.WITH_KEYWORD) {
      parseWithStatement(myBuilder.mark());
      return;
    }
    if (firstToken == PyTokenTypes.ASYNC_KEYWORD) {
      parseAsyncStatement(false);
      return;
    }
    if (atToken(PyTokenTypes.IDENTIFIER, TOK_TYPE)) {
      if (parseTypeAliasStatement()) {
        return;
      }
    }

    if (atToken(PyTokenTypes.IDENTIFIER, TOK_ASYNC)) {
      if (parseAsyncStatement(true)) {
        return;
      }
    }
    if (atToken(PyTokenTypes.IDENTIFIER, TOK_MATCH)) {
      if (parseMatchStatement()) {
        return;
      }
    }

    parseSimpleStatement();
  }

  private boolean parseTypeAliasStatement() {
    assert atToken(PyTokenTypes.IDENTIFIER, TOK_TYPE);
    SyntaxTreeBuilder.Marker mark = myBuilder.mark();
    myBuilder.remapCurrentToken(PyTokenTypes.TYPE_KEYWORD);
    nextToken();
    if (!atToken(PyTokenTypes.IDENTIFIER)) {
      mark.rollbackTo();
      myBuilder.remapCurrentToken(PyTokenTypes.IDENTIFIER);
      return false;
    }
    parseIdentifierOrSkip(PyTokenTypes.LBRACKET, PyTokenTypes.EQ);
    parseTypeParameterList();
    checkMatches(PyTokenTypes.EQ, PyParsingBundle.message("PARSE.eq.expected"));
    myContext.getExpressionParser().parseExpression();
    checkEndOfStatement();
    mark.done(PyElementTypes.TYPE_ALIAS_STATEMENT);
    return true;
  }

  private boolean parseMatchStatement() {
    assert atToken(PyTokenTypes.IDENTIFIER, TOK_MATCH);
    SyntaxTreeBuilder.Marker mark = myBuilder.mark();
    myBuilder.remapCurrentToken(PyTokenTypes.MATCH_KEYWORD);
    myBuilder.advanceLexer();
    boolean followedBySubject = myContext.getExpressionParser().parseExpressionOptional();
    if (!followedBySubject || !matchToken(PyTokenTypes.COLON)) {
      mark.rollbackTo();
      myBuilder.remapCurrentToken(PyTokenTypes.IDENTIFIER);
      return false;
    }
    parseCaseClauses();
    mark.done(PyElementTypes.MATCH_STATEMENT);
    return true;
  }

  private boolean parseCaseClauses() {
    if (myBuilder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
      myBuilder.advanceLexer();

      final boolean indentFound = myBuilder.getTokenType() == PyTokenTypes.INDENT;
      if (indentFound) {
        myBuilder.advanceLexer();
        while (!myBuilder.eof() && myBuilder.getTokenType() != PyTokenTypes.DEDENT) {
          if (!parseCaseClause()) {
            SyntaxTreeBuilder.Marker illegalStatement = myBuilder.mark();
            parseStatement();
            illegalStatement.error(PyParsingBundle.message("PARSE.expected.case.clause"));
          }
        }
        if (!myBuilder.eof()) {
          assert myBuilder.getTokenType() == PyTokenTypes.DEDENT;
          myBuilder.advanceLexer();
        }
      }
      else {
        myBuilder.error(PyParsingBundle.message("indent.expected"));
        return false;
      }
    }
    return true;
  }

  private boolean parseCaseClause() {
    if (atToken(PyTokenTypes.IDENTIFIER, TOK_CASE)) {
      SyntaxTreeBuilder.Marker mark = myBuilder.mark();
      myBuilder.remapCurrentToken(PyTokenTypes.CASE_KEYWORD);
      myBuilder.advanceLexer();
      if (!getPatternParser().parseCasePattern()) {
        SyntaxTreeBuilder.Marker patternError = myBuilder.mark();
        while (!myBuilder.eof() && !atAnyOfTokens(PyTokenTypes.IF_KEYWORD, PyTokenTypes.COLON, PyTokenTypes.STATEMENT_BREAK)) {
          nextToken();
        }
        patternError.error(PyParsingBundle.message("PARSE.expected.pattern"));
      }
      if (matchToken(PyTokenTypes.IF_KEYWORD)) {
        if (!getExpressionParser().parseNamedTestExpression(false, false)) {
          myBuilder.error(PyParsingBundle.message("PARSE.expected.expression"));
        }
      }
      parseColonAndSuite();
      mark.done(PyElementTypes.CASE_CLAUSE);
      return true;
    }
    return false;
  }

  protected void parseSimpleStatement() {
    parseSimpleStatement(true);
  }

  protected void parseSimpleStatement(boolean checkLanguageLevel) {
    SyntaxTreeBuilder builder = myContext.getBuilder();
    final IElementType firstToken = builder.getTokenType();
    if (firstToken == null) {
      return;
    }
    if (firstToken == PyTokenTypes.PRINT_KEYWORD && hasPrintStatement()) {
      parsePrintStatement(builder);
      return;
    }
    if (firstToken == PyTokenTypes.ASSERT_KEYWORD) {
      parseAssertStatement();
      return;
    }
    if (firstToken == PyTokenTypes.BREAK_KEYWORD) {
      parseKeywordStatement(builder, PyElementTypes.BREAK_STATEMENT);
      return;
    }
    if (firstToken == PyTokenTypes.CONTINUE_KEYWORD) {
      parseKeywordStatement(builder, PyElementTypes.CONTINUE_STATEMENT);
      return;
    }
    if (firstToken == PyTokenTypes.DEL_KEYWORD) {
      parseDelStatement();
      return;
    }
    if (firstToken == PyTokenTypes.EXEC_KEYWORD) {
      parseExecStatement();
      return;
    }
    if (firstToken == PyTokenTypes.GLOBAL_KEYWORD) {
      parseNameDefiningStatement(PyElementTypes.GLOBAL_STATEMENT);
      return;
    }
    if (firstToken == PyTokenTypes.NONLOCAL_KEYWORD) {
      parseNameDefiningStatement(PyElementTypes.NONLOCAL_STATEMENT);
      return;
    }
    if (firstToken == PyTokenTypes.IMPORT_KEYWORD) {
      parseImportStatement(PyElementTypes.IMPORT_STATEMENT, PyElementTypes.IMPORT_ELEMENT);
      return;
    }
    if (firstToken == PyTokenTypes.FROM_KEYWORD) {
      parseFromImportStatement();
      return;
    }
    if (firstToken == PyTokenTypes.PASS_KEYWORD) {
      parseKeywordStatement(builder, PyElementTypes.PASS_STATEMENT);
      return;
    }
    if (firstToken == PyTokenTypes.RETURN_KEYWORD) {
      parseReturnStatement(builder);
      return;
    }
    if (firstToken == PyTokenTypes.RAISE_KEYWORD) {
      parseRaiseStatement();
      return;
    }
    SyntaxTreeBuilder.Marker exprStatement = builder.mark();
    if (builder.getTokenType() == PyTokenTypes.YIELD_KEYWORD) {
      getExpressionParser().parseYieldOrTupleExpression(false);
      checkEndOfStatement();
      exprStatement.done(PyElementTypes.EXPRESSION_STATEMENT);
      return;
    }
    else if (getExpressionParser().parseExpressionOptional()) {
      IElementType statementType = PyElementTypes.EXPRESSION_STATEMENT;
      if (PyTokenTypes.AUG_ASSIGN_OPERATIONS.contains(builder.getTokenType())) {
        statementType = PyElementTypes.AUG_ASSIGNMENT_STATEMENT;
        builder.advanceLexer();
        if (!getExpressionParser().parseYieldOrTupleExpression(false)) {
          builder.error(PyParsingBundle.message("PARSE.expected.expression"));
        }
      }
      else if (atToken(PyTokenTypes.EQ) || (atToken(PyTokenTypes.COLON) && (!checkLanguageLevel || myContext.getLanguageLevel().isPy3K()))) {
        exprStatement.rollbackTo();
        exprStatement = builder.mark();
        getExpressionParser().parseExpression(false, true);
        LOG.assertTrue(builder.getTokenType() == PyTokenTypes.EQ || builder.getTokenType() == PyTokenTypes.COLON, builder.getTokenType());

        if (builder.getTokenType() == PyTokenTypes.COLON) {
          statementType = PyElementTypes.TYPE_DECLARATION_STATEMENT;
          getFunctionParser().parseParameterAnnotation();
        }

        if (builder.getTokenType() == PyTokenTypes.EQ) {
          statementType = PyElementTypes.ASSIGNMENT_STATEMENT;
          builder.advanceLexer();

          while (true) {
            SyntaxTreeBuilder.Marker maybeExprMarker = builder.mark();
            final boolean isYieldExpr = builder.getTokenType() == PyTokenTypes.YIELD_KEYWORD;
            if (!getExpressionParser().parseYieldOrTupleExpression(false)) {
              maybeExprMarker.drop();
              builder.error(PyParsingBundle.message("PARSE.expected.expression"));
              break;
            }
            if (builder.getTokenType() == PyTokenTypes.EQ) {
              if (isYieldExpr) {
                maybeExprMarker.drop();
                builder.error(PyParsingBundle.message("cannot.assign.to.yield.expression"));
              }
              else {
                maybeExprMarker.rollbackTo();
                getExpressionParser().parseExpression(false, true);
                LOG.assertTrue(builder.getTokenType() == PyTokenTypes.EQ, builder.getTokenType());
              }
              builder.advanceLexer();
            }
            else {
              maybeExprMarker.drop();
              break;
            }
          }
        }
      }

      checkEndOfStatement();
      exprStatement.done(statementType);
      return;
    }
    else {
      exprStatement.drop();
    }

    builder.advanceLexer();
    reportParseStatementError(builder, firstToken);
  }

  protected void reportParseStatementError(SyntaxTreeBuilder builder, IElementType firstToken) {
    if (firstToken == PyTokenTypes.INCONSISTENT_DEDENT) {
      builder.error(PyParsingBundle.message("unindent.does.not.match.any.outer.indent"));
    }
    else if (firstToken == PyTokenTypes.INDENT) {
      builder.error(PyParsingBundle.message("unexpected.indent"));
    }
    else {
      builder.error(PyParsingBundle.message("statement.expected.found.0", firstToken.toString()));
    }
  }

  protected boolean hasPrintStatement() {
    return myContext.getLanguageLevel().hasPrintStatement() && !myFutureFlags.contains(FutureFeature.PRINT_FUNCTION);
  }

  protected void checkEndOfStatement() {
    SyntaxTreeBuilder builder = myContext.getBuilder();
    final ParsingScope scope = getParsingContext().getScope();
    if (builder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
      builder.advanceLexer();
      scope.setAfterSemicolon(false);
    }
    else if (builder.getTokenType() == PyTokenTypes.SEMICOLON) {
      if (!scope.isSuite()) {
        builder.advanceLexer();
        scope.setAfterSemicolon(true);
        if (builder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
          builder.advanceLexer();
          scope.setAfterSemicolon(false);
        }
      }
    }
    else if (!builder.eof()) {
      builder.error(PyParsingBundle.message("end.of.statement.expected"));
    }
  }

  private void parsePrintStatement(final SyntaxTreeBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.PRINT_KEYWORD);
    final SyntaxTreeBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() == PyTokenTypes.GTGT) {
      final SyntaxTreeBuilder.Marker target = builder.mark();
      builder.advanceLexer();
      getExpressionParser().parseSingleExpression(false);
      target.done(PyElementTypes.PRINT_TARGET);
    }
    else {
      getExpressionParser().parseSingleExpression(false);
    }
    while (builder.getTokenType() == PyTokenTypes.COMMA) {
      builder.advanceLexer();
      if (getEndOfStatementsTokens().contains(builder.getTokenType())) {
        break;
      }
      getExpressionParser().parseSingleExpression(false);
    }
    checkEndOfStatement();
    statement.done(PyElementTypes.PRINT_STATEMENT);
  }

  protected void parseKeywordStatement(SyntaxTreeBuilder builder, IElementType statementType) {
    final SyntaxTreeBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    checkEndOfStatement();
    statement.done(statementType);
  }

  private void parseReturnStatement(SyntaxTreeBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.RETURN_KEYWORD);
    final SyntaxTreeBuilder.Marker returnStatement = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() != null && !getEndOfStatementsTokens().contains(builder.getTokenType())) {
      getExpressionParser().parseExpression();
    }
    checkEndOfStatement();
    returnStatement.done(PyElementTypes.RETURN_STATEMENT);
  }

  private void parseDelStatement() {
    assertCurrentToken(PyTokenTypes.DEL_KEYWORD);
    final SyntaxTreeBuilder.Marker delStatement = myBuilder.mark();
    myBuilder.advanceLexer();
    if (!getExpressionParser().parseSingleExpression(false)) {
      myBuilder.error(PyParsingBundle.message("PARSE.expected.expression"));
    }
    while (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
      myBuilder.advanceLexer();
      if (!getEndOfStatementsTokens().contains(myBuilder.getTokenType())) {
        if (!getExpressionParser().parseSingleExpression(false)) {
          myBuilder.error(PyParsingBundle.message("PARSE.expected.expression"));
        }
      }
    }

    checkEndOfStatement();
    delStatement.done(PyElementTypes.DEL_STATEMENT);
  }

  private void parseRaiseStatement() {
    assertCurrentToken(PyTokenTypes.RAISE_KEYWORD);
    final SyntaxTreeBuilder.Marker raiseStatement = myBuilder.mark();
    myBuilder.advanceLexer();
    if (!getEndOfStatementsTokens().contains(myBuilder.getTokenType())) {
      getExpressionParser().parseSingleExpression(false);
      if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
        myBuilder.advanceLexer();
        getExpressionParser().parseSingleExpression(false);
        if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
          myBuilder.advanceLexer();
          getExpressionParser().parseSingleExpression(false);
        }
      }
      else if (myBuilder.getTokenType() == PyTokenTypes.FROM_KEYWORD) {
        myBuilder.advanceLexer();
        if (!getExpressionParser().parseSingleExpression(false)) {
          myBuilder.error(PyParsingBundle.message("PARSE.expected.expression"));
        }
      }
    }
    checkEndOfStatement();
    raiseStatement.done(PyElementTypes.RAISE_STATEMENT);
  }

  private void parseAssertStatement() {
    assertCurrentToken(PyTokenTypes.ASSERT_KEYWORD);
    final SyntaxTreeBuilder.Marker assertStatement = myBuilder.mark();
    myBuilder.advanceLexer();
    if (getExpressionParser().parseSingleExpression(false)) {
      if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
        myBuilder.advanceLexer();
        if (!getExpressionParser().parseSingleExpression(false)) {
          myContext.getBuilder().error(PyParsingBundle.message("PARSE.expected.expression"));
        }
      }
      checkEndOfStatement();
    }
    else {
      myContext.getBuilder().error(PyParsingBundle.message("PARSE.expected.expression"));
    }
    assertStatement.done(PyElementTypes.ASSERT_STATEMENT);
  }

  protected void parseImportStatement(IElementType statementType, IElementType elementType) {
    final SyntaxTreeBuilder builder = myContext.getBuilder();
    final SyntaxTreeBuilder.Marker importStatement = builder.mark();
    builder.advanceLexer();
    parseImportElements(elementType, true, false, false);
    checkEndOfStatement();
    importStatement.done(statementType);
  }

  /*
  Really parses two forms:
  from identifier import id, id... -- may be either relative or absolute
  from . import identifier -- only relative
   */
  private void parseFromImportStatement() {
    SyntaxTreeBuilder builder = myContext.getBuilder();
    assertCurrentToken(PyTokenTypes.FROM_KEYWORD);
    myFutureImportPhase = Phase.FROM;
    final SyntaxTreeBuilder.Marker fromImportStatement = builder.mark();
    builder.advanceLexer();
    boolean from_future = false;
    boolean had_dots = parseRelativeImportDots();
    IElementType statementType = PyElementTypes.FROM_IMPORT_STATEMENT;
    if (had_dots && parseOptionalDottedName() || parseDottedName()) {
      final ImportTypes types = checkFromImportKeyword();
      statementType = types.statement;
      final IElementType elementType = types.element;
      if (myFutureImportPhase == Phase.FUTURE) {
        myFutureImportPhase = Phase.IMPORT;
        from_future = true;
      }
      if (builder.getTokenType() == PyTokenTypes.MULT) {
        final SyntaxTreeBuilder.Marker star_import_mark = builder.mark();
        builder.advanceLexer();
        star_import_mark.done(types.starElement);
      }
      else if (builder.getTokenType() == PyTokenTypes.LPAR) {
        builder.advanceLexer();
        parseImportElements(elementType, false, true, from_future);
        checkMatches(PyTokenTypes.RPAR, PyParsingBundle.message("PARSE.expected.rpar"));
      }
      else {
        parseImportElements(elementType, false, false, from_future);
      }
    }
    else if (had_dots) { // from . import ...
      final ImportTypes types = checkFromImportKeyword();
      statementType = types.statement;
      parseImportElements(types.element, false, false, from_future);
    }
    checkEndOfStatement();
    fromImportStatement.done(statementType);
    myFutureImportPhase = Phase.NONE;
  }

  protected ImportTypes checkFromImportKeyword() {
    checkMatches(PyTokenTypes.IMPORT_KEYWORD, PyParsingBundle.message("import.expected"));
    return new ImportTypes(PyElementTypes.FROM_IMPORT_STATEMENT, PyElementTypes.IMPORT_ELEMENT, PyElementTypes.STAR_IMPORT_ELEMENT);
  }

  /**
   * Parses option dots before imported name.
   *
   * @return true iff there were dots.
   */
  private boolean parseRelativeImportDots() {
    SyntaxTreeBuilder builder = myContext.getBuilder();
    boolean had_dots = false;
    while (builder.getTokenType() == PyTokenTypes.DOT) {
      had_dots = true;
      builder.advanceLexer();
    }
    return had_dots;
  }

  private void parseImportElements(IElementType elementType, boolean is_module_import, boolean in_parens, final boolean from_future) {
    SyntaxTreeBuilder builder = myContext.getBuilder();
    while (true) {
      final SyntaxTreeBuilder.Marker asMarker = builder.mark();
      if (is_module_import) { // import _
        if (!parseDottedNameAsAware(false)) {
          asMarker.drop();
          break;
        }
      }
      else { // from X import _
        String token_text = parseIdentifier(getReferenceType());
        if (from_future) {
          // TODO: mark all known future feature names
          if (TOK_PRINT_FUNCTION.equals(token_text)) {
            myFutureFlags.add(FutureFeature.PRINT_FUNCTION);
          }
        }
      }
      if (builder.getTokenType() == PyTokenTypes.AS_KEYWORD) {
        builder.advanceLexer();
        parseIdentifier(PyElementTypes.TARGET_EXPRESSION);
      }
      asMarker.done(elementType);
      if (builder.getTokenType() == PyTokenTypes.COMMA) {
        builder.advanceLexer();
        if (in_parens && builder.getTokenType() == PyTokenTypes.RPAR) {
          break;
        }
      }
      else {
        break;
      }
    }
  }

  private @Nullable String parseIdentifier(final IElementType elementType) {
    final SyntaxTreeBuilder.Marker idMarker = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
      String id_text = myBuilder.getTokenText();
      myBuilder.advanceLexer();
      idMarker.done(elementType);
      return id_text;
    }
    else {
      myBuilder.error(PyParsingBundle.message("PARSE.expected.identifier"));
      idMarker.drop();
    }
    return null;
  }

  public boolean parseOptionalDottedName() {
    return parseDottedNameAsAware(true);
  }

  public boolean parseDottedName() {
    return parseDottedNameAsAware(false);
  }

  // true if identifier was parsed or skipped as optional, false on error
  protected boolean parseDottedNameAsAware(boolean optional) {
    if (myBuilder.getTokenType() != PyTokenTypes.IDENTIFIER) {
      if (optional) return true;
      myBuilder.error(PyParsingBundle.message("PARSE.expected.identifier"));
      return false;
    }
    SyntaxTreeBuilder.Marker marker = myBuilder.mark();
    myBuilder.advanceLexer();
    marker.done(getReferenceType());
    while (myBuilder.getTokenType() == PyTokenTypes.DOT) {
      marker = marker.precede();
      myBuilder.advanceLexer();
      checkMatches(PyTokenTypes.IDENTIFIER, PyParsingBundle.message("PARSE.expected.identifier"));
      marker.done(getReferenceType());
    }
    return true;
  }

  private void parseNameDefiningStatement(final PyElementType elementType) {
    final SyntaxTreeBuilder.Marker globalStatement = myBuilder.mark();
    myBuilder.advanceLexer();
    parseIdentifier(PyElementTypes.TARGET_EXPRESSION);
    while (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
      myBuilder.advanceLexer();
      parseIdentifier(PyElementTypes.TARGET_EXPRESSION);
    }
    checkEndOfStatement();
    globalStatement.done(elementType);
  }

  private void parseExecStatement() {
    assertCurrentToken(PyTokenTypes.EXEC_KEYWORD);
    final SyntaxTreeBuilder.Marker execStatement = myBuilder.mark();
    myBuilder.advanceLexer();
    getExpressionParser().parseExpression(true, false);
    if (myBuilder.getTokenType() == PyTokenTypes.IN_KEYWORD) {
      myBuilder.advanceLexer();
      getExpressionParser().parseSingleExpression(false);
      if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
        myBuilder.advanceLexer();
        getExpressionParser().parseSingleExpression(false);
      }
    }
    checkEndOfStatement();
    execStatement.done(PyElementTypes.EXEC_STATEMENT);
  }

  protected void parseIfStatement(PyElementType ifKeyword, PyElementType elifKeyword, PyElementType elseKeyword,
                                  PyElementType elementType) {
    assertCurrentToken(ifKeyword);
    final SyntaxTreeBuilder.Marker ifStatement = myBuilder.mark();
    final SyntaxTreeBuilder.Marker ifPart = myBuilder.mark();
    myBuilder.advanceLexer();
    if (!getExpressionParser().parseNamedTestExpression(false, false)) {
      myBuilder.error(PyParsingBundle.message("PARSE.expected.expression"));
    }
    parseColonAndSuite();
    ifPart.done(PyElementTypes.IF_PART_IF);
    SyntaxTreeBuilder.Marker elifPart = myBuilder.mark();
    while (myBuilder.getTokenType() == elifKeyword) {
      myBuilder.advanceLexer();
      if (!getExpressionParser().parseNamedTestExpression(false, false)) {
        myBuilder.error(PyParsingBundle.message("PARSE.expected.expression"));
      }
      parseColonAndSuite();
      elifPart.done(PyElementTypes.IF_PART_ELIF);
      elifPart = myBuilder.mark();
    }
    elifPart.drop(); // we always kept an open extra elif
    final SyntaxTreeBuilder.Marker elsePart = myBuilder.mark();
    if (myBuilder.getTokenType() == elseKeyword) {
      myBuilder.advanceLexer();
      parseColonAndSuite();
      elsePart.done(PyElementTypes.ELSE_PART);
    }
    else {
      elsePart.drop();
    }
    ifStatement.done(elementType);
  }

  private boolean expectColon() {
    if (myBuilder.getTokenType() == PyTokenTypes.COLON) {
      myBuilder.advanceLexer();
      return true;
    }
    else if (myBuilder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
      myBuilder.error(PyParsingBundle.message("PARSE.expected.colon"));
      return true;
    }
    final SyntaxTreeBuilder.Marker marker = myBuilder.mark();
    while (!atAnyOfTokens(null, PyTokenTypes.DEDENT, PyTokenTypes.STATEMENT_BREAK, PyTokenTypes.COLON)) {
      myBuilder.advanceLexer();
    }
    boolean result = matchToken(PyTokenTypes.COLON);
    if (!result && atToken(PyTokenTypes.STATEMENT_BREAK)) {
      myBuilder.advanceLexer();
    }
    marker.error(PyParsingBundle.message("PARSE.expected.colon"));
    return result;
  }

  private void parseForStatement(SyntaxTreeBuilder.Marker endMarker) {
    assertCurrentToken(PyTokenTypes.FOR_KEYWORD);
    parseForPart();
    final SyntaxTreeBuilder.Marker elsePart = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
      myBuilder.advanceLexer();
      parseColonAndSuite();
      elsePart.done(PyElementTypes.ELSE_PART);
    }
    else {
      elsePart.drop();
    }
    endMarker.done(PyElementTypes.FOR_STATEMENT);
  }

  protected void parseForPart() {
    final SyntaxTreeBuilder.Marker forPart = myBuilder.mark();
    myBuilder.advanceLexer();
    getExpressionParser().parseStarTargets();
    checkMatches(PyTokenTypes.IN_KEYWORD, PyParsingBundle.message("PARSE.expected.in"));
    getExpressionParser().parseExpression();
    parseColonAndSuite();
    forPart.done(PyElementTypes.FOR_PART);
  }

  private void parseWhileStatement() {
    assertCurrentToken(PyTokenTypes.WHILE_KEYWORD);
    final SyntaxTreeBuilder.Marker statement = myBuilder.mark();
    final SyntaxTreeBuilder.Marker whilePart = myBuilder.mark();
    myBuilder.advanceLexer();
    if (!getExpressionParser().parseNamedTestExpression(false, false)) {
      myBuilder.error(PyParsingBundle.message("PARSE.expected.expression"));
    }
    parseColonAndSuite();
    whilePart.done(PyElementTypes.WHILE_PART);
    final SyntaxTreeBuilder.Marker elsePart = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
      myBuilder.advanceLexer();
      parseColonAndSuite();
      elsePart.done(PyElementTypes.ELSE_PART);
    }
    else {
      elsePart.drop();
    }
    statement.done(PyElementTypes.WHILE_STATEMENT);
  }

  private void parseTryStatement() {
    assertCurrentToken(PyTokenTypes.TRY_KEYWORD);
    final SyntaxTreeBuilder.Marker statement = myBuilder.mark();
    final SyntaxTreeBuilder.Marker tryPart = myBuilder.mark();
    myBuilder.advanceLexer();
    parseColonAndSuite();
    tryPart.done(PyElementTypes.TRY_PART);
    boolean haveExceptClause = false;
    if (myBuilder.getTokenType() == PyTokenTypes.EXCEPT_KEYWORD) {
      haveExceptClause = true;
      while (myBuilder.getTokenType() == PyTokenTypes.EXCEPT_KEYWORD) {
        final SyntaxTreeBuilder.Marker exceptBlock = myBuilder.mark();
        myBuilder.advanceLexer();

        boolean star = matchToken(PyTokenTypes.MULT);
        if (myBuilder.getTokenType() != PyTokenTypes.COLON) {
          if (!getExpressionParser().parseSingleExpression(false)) {
            myBuilder.error(PyParsingBundle.message("PARSE.expected.expression"));
          }
          if (myBuilder.getTokenType() == PyTokenTypes.COMMA || myBuilder.getTokenType() == PyTokenTypes.AS_KEYWORD) {
            myBuilder.advanceLexer();
            if (!getExpressionParser().parseSingleExpression(true)) {
              myBuilder.error(PyParsingBundle.message("PARSE.expected.expression"));
            }
          }
        }
        else if (star) {
          myBuilder.error(PyParsingBundle.message("PARSE.expected.expression"));
        }
        parseColonAndSuite();
        exceptBlock.done(PyElementTypes.EXCEPT_PART);
      }
      final SyntaxTreeBuilder.Marker elsePart = myBuilder.mark();
      if (myBuilder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
        myBuilder.advanceLexer();
        parseColonAndSuite();
        elsePart.done(PyElementTypes.ELSE_PART);
      }
      else {
        elsePart.drop();
      }
    }
    final SyntaxTreeBuilder.Marker finallyPart = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.FINALLY_KEYWORD) {
      myBuilder.advanceLexer();
      parseColonAndSuite();
      finallyPart.done(PyElementTypes.FINALLY_PART);
    }
    else {
      finallyPart.drop();
      if (!haveExceptClause) {
        myBuilder.error(PyParsingBundle.message("except.or.finally.expected"));
        // much better to have a statement of incorrectly determined type
        // than "TRY" and "COLON" tokens attached to nothing
      }
    }
    statement.done(PyElementTypes.TRY_EXCEPT_STATEMENT);
  }

  private void parseColonAndSuite() {
    if (expectColon()) {
      parseSuite();
    }
    else {
      final SyntaxTreeBuilder.Marker mark = myBuilder.mark();
      mark.done(PyElementTypes.STATEMENT_LIST);
    }
  }

  private void parseWithStatement(SyntaxTreeBuilder.Marker endMarker) {
    assertCurrentToken(PyTokenTypes.WITH_KEYWORD);
    myBuilder.advanceLexer();
    if (!parseParenthesizedWithItems()) {
      if (!parseWithItems(false)) {
        myBuilder.error(PyParsingBundle.message("PARSE.expected.expression"));
      }
    }
    parseColonAndSuite();
    endMarker.done(PyElementTypes.WITH_STATEMENT);
  }

  private boolean parseParenthesizedWithItems() {
    if (!atToken(PyTokenTypes.LPAR)) {
      return false;
    }
    final SyntaxTreeBuilder.Marker leftPar = myBuilder.mark();
    nextToken();
    // Reparse empty parentheses as an empty tuple
    if (!parseWithItems(true)) {
      leftPar.rollbackTo();
      return false;
    }
    if (!matchToken(PyTokenTypes.RPAR)) {
      myBuilder.error(PyParsingBundle.message("PARSE.expected.rpar"));
    }
    // Reparse something like "(foo()) as bar" or (foo()).bar as a single WithItem
    if (!atAnyOfTokens(PyTokenTypes.COLON, PyTokenTypes.STATEMENT_BREAK)) {
      leftPar.rollbackTo();
      return false;
    }
    leftPar.drop();
    return true;
  }

  private boolean parseWithItems(boolean insideParentheses) {
    if (!parseWithItem()) {
      return false;
    }
    while (matchToken(PyTokenTypes.COMMA)) {
      if (!parseWithItem()) {
        if (!insideParentheses) {
          myBuilder.error(PyParsingBundle.message("PARSE.expected.expression"));
        }
        break;
      }
    }
    return true;
  }

  private boolean parseWithItem() {
    SyntaxTreeBuilder.Marker withItem = myBuilder.mark();
    if (!getExpressionParser().parseSingleExpression(false)) {
      withItem.drop();
      return false;
    }
    if (matchToken(PyTokenTypes.AS_KEYWORD)) {
      if (!getExpressionParser().parseSingleExpression(true)) {
        myBuilder.error(PyParsingBundle.message("PARSE.expected.identifier"));
      }
    }
    withItem.done(PyElementTypes.WITH_ITEM);
    return true;
  }

  private void parseClassDeclaration() {
    final SyntaxTreeBuilder.Marker classMarker = myBuilder.mark();
    parseClassDeclaration(classMarker);
  }

  public void parseClassDeclaration(SyntaxTreeBuilder.Marker classMarker) {
    assertCurrentToken(PyTokenTypes.CLASS_KEYWORD);
    myBuilder.advanceLexer();
    parseIdentifierOrSkip(PyTokenTypes.LPAR, PyTokenTypes.LBRACKET, PyTokenTypes.COLON);
    parseTypeParameterList();
    if (myBuilder.getTokenType() == PyTokenTypes.LPAR) {
      getExpressionParser().parseArgumentList();
    }
    else {
      final SyntaxTreeBuilder.Marker inheritMarker = myBuilder.mark();
      inheritMarker.done(PyElementTypes.ARGUMENT_LIST);
    }
    final ParsingContext context = getParsingContext();
    context.pushScope(context.getScope().withClass());
    parseColonAndSuite();
    context.popScope();
    classMarker.done(PyElementTypes.CLASS_DECLARATION);
  }

  private boolean parseAsyncStatement(boolean falseAsync) {
    if (!falseAsync) {
      assertCurrentToken(PyTokenTypes.ASYNC_KEYWORD);
    }
    final SyntaxTreeBuilder.Marker marker = myBuilder.mark();

    advanceAsync(falseAsync);

    final IElementType token = myBuilder.getTokenType();
    if (token == PyTokenTypes.DEF_KEYWORD) {
      getFunctionParser().parseFunctionDeclaration(marker, true);
      return true;
    }
    else if (token == PyTokenTypes.WITH_KEYWORD) {
      parseWithStatement(marker);
      return true;
    }
    else if (token == PyTokenTypes.FOR_KEYWORD) {
      parseForStatement(marker);
      return true;
    }
    else {
      if (falseAsync) {
        marker.rollbackTo();
      }
      else {
        marker.drop();
        myBuilder.error(PyParsingBundle.message("def.or.with.or.for.expected"));
      }
      return false;
    }
  }

  public void parseSuite() {
    parseSuite(null, null);
  }

  public void parseSuite(@Nullable SyntaxTreeBuilder.Marker endMarker, @Nullable IElementType elType) {
    if (myBuilder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
      myBuilder.advanceLexer();

      final SyntaxTreeBuilder.Marker marker = myBuilder.mark();
      final boolean indentFound = myBuilder.getTokenType() == PyTokenTypes.INDENT;
      if (indentFound) {
        myBuilder.advanceLexer();
        while (!myBuilder.eof() && myBuilder.getTokenType() != PyTokenTypes.DEDENT) {
          parseStatement();
        }
      }
      else {
        myBuilder.error(PyParsingBundle.message("indent.expected"));
      }

      marker.done(PyElementTypes.STATEMENT_LIST);
      marker.setCustomEdgeTokenBinders(LeadingCommentsBinder.INSTANCE, FollowingCommentBinder.INSTANCE);
      if (endMarker != null) {
        endMarker.done(elType);
      }
      if (indentFound && !myBuilder.eof()) {
        assert myBuilder.getTokenType() == PyTokenTypes.DEDENT;
        myBuilder.advanceLexer();
      }
    }
    else {
      final SyntaxTreeBuilder.Marker marker = myBuilder.mark();
      if (myBuilder.eof()) {
        myBuilder.error(PyParsingBundle.message("expected.statement"));
      }
      else {
        final ParsingContext context = getParsingContext();
        context.pushScope(context.getScope().withSuite());
        parseSimpleStatement();
        context.popScope();
        while (matchToken(PyTokenTypes.SEMICOLON)) {
          if (matchToken(PyTokenTypes.STATEMENT_BREAK)) {
            break;
          }
          context.pushScope(context.getScope().withSuite());
          parseSimpleStatement();
          context.popScope();
        }
      }
      marker.done(PyElementTypes.STATEMENT_LIST);
      if (endMarker != null) {
        endMarker.done(elType);
      }
    }
  }

  public void parseTypeParameterList() {
    if (atToken(PyTokenTypes.LBRACKET)) {
      final SyntaxTreeBuilder.Marker typeParamList = myBuilder.mark();
      nextToken();
      do {
        if (!parseTypeParameter()) {
          myBuilder.error(PyParsingBundle.message("PARSE.expected.type.parameter"));
        }

        if (atToken(PyTokenTypes.COMMA)) {
          nextToken();
        }
        else if (!atToken(PyTokenTypes.RBRACKET)) {
          break;
        }
      }
      while (!atToken(PyTokenTypes.RBRACKET));
      checkMatches(PyTokenTypes.RBRACKET, PyParsingBundle.message("PARSE.expected.symbols", ",", "]"));
      typeParamList.done(PyElementTypes.TYPE_PARAMETER_LIST);
    }
  }

  private boolean parseTypeParameter() {
    if (isIdentifier(myBuilder) || atToken(PyTokenTypes.MULT) || atToken(PyTokenTypes.EXP)) {
      SyntaxTreeBuilder.Marker typeParamMarker = myBuilder.mark();

      if (atAnyOfTokens(PyTokenTypes.MULT, PyTokenTypes.EXP)) {
        nextToken();
      }

      if (!parseIdentifierOrSkip(PyTokenTypes.RBRACKET, PyTokenTypes.COMMA, PyTokenTypes.COLON, PyTokenTypes.EQ)) {
        typeParamMarker.drop();
        return false;
      }

      if (matchToken(PyTokenTypes.COLON)) {
        if (!myContext.getExpressionParser().parseSingleExpression(false)) {
          myBuilder.error(PyParsingBundle.message("PARSE.expected.expression"));
        }
      }

      if (matchToken(PyTokenTypes.EQ)) {
        if (!myContext.getExpressionParser().parseSingleExpression(false)) {
          myBuilder.error(PyParsingBundle.message("PARSE.expected.expression"));
        }
      }
      typeParamMarker.done(PyElementTypes.TYPE_PARAMETER);
      return true;
    }
    return false;
  }

  @Override
  public IElementType filter(final IElementType source, final int start, final int end, final CharSequence text) {
    return filter(source, start, end, text, true);
  }

  protected IElementType filter(final IElementType source, final int start, final int end, final CharSequence text, boolean checkLanguageLevel) {
    if (source == PyTokenTypes.IDENTIFIER && isWordAtPosition(text, start, end, TOK_AS)) {
      return PyTokenTypes.AS_KEYWORD;
    }
    else if ( // filter
      (myFutureImportPhase == Phase.FROM) &&
      source == PyTokenTypes.IDENTIFIER &&
      isWordAtPosition(text, start, end, TOK_FUTURE_IMPORT)
      ) {
      myFutureImportPhase = Phase.FUTURE;
      return source;
    }
    else if (source == PyTokenTypes.IDENTIFIER && isWordAtPosition(text, start, end, TOK_WITH)) {
      return PyTokenTypes.WITH_KEYWORD;
    }
    else if (hasPrintStatement() &&
             source == PyTokenTypes.IDENTIFIER &&
             isWordAtPosition(text, start, end, TOK_PRINT)) {
      return PyTokenTypes.PRINT_KEYWORD;
    }
    else if ((myContext.getLanguageLevel().isPy3K() || !checkLanguageLevel) &&
             source == PyTokenTypes.IDENTIFIER) {
      if (isWordAtPosition(text, start, end, TOK_NONE)) {
        return PyTokenTypes.NONE_KEYWORD;
      }
      if (isWordAtPosition(text, start, end, TOK_TRUE)) {
        return PyTokenTypes.TRUE_KEYWORD;
      }
      if (isWordAtPosition(text, start, end, TOK_FALSE)) {
        return PyTokenTypes.FALSE_KEYWORD;
      }
      if (isWordAtPosition(text, start, end, TOK_DEBUG)) {
        return PyTokenTypes.DEBUG_KEYWORD;
      }
      if (isWordAtPosition(text, start, end, TOK_NONLOCAL)) {
        return PyTokenTypes.NONLOCAL_KEYWORD;
      }
      final LanguageLevel languageLevel = myContext.getLanguageLevel();
      if (languageLevel.isAtLeast(LanguageLevel.PYTHON35) || !checkLanguageLevel) {
        if (isWordAtPosition(text, start, end, TOK_ASYNC)) {
          if (languageLevel.isAtLeast(LanguageLevel.PYTHON37) ||
              myContext.getScope().isAsync() ||
              myBuilder.lookAhead(1) == PyTokenTypes.DEF_KEYWORD) {
            return PyTokenTypes.ASYNC_KEYWORD;
          }
        }
        if (isWordAtPosition(text, start, end, TOK_AWAIT)) {
          if (languageLevel.isAtLeast(LanguageLevel.PYTHON37) || myContext.getScope().isAsync()) {
            return PyTokenTypes.AWAIT_KEYWORD;
          }
        }
      }
    }
    else if ((myContext.getLanguageLevel().isPython2() || !checkLanguageLevel) &&
             source == PyTokenTypes.IDENTIFIER) {
      if (isWordAtPosition(text, start, end, TOK_EXEC)) {
        return PyTokenTypes.EXEC_KEYWORD;
      }
    }
    return source;
  }

  protected TokenSet getEndOfStatementsTokens() {
    return PyTokenTypes.END_OF_STATEMENT;
  }

  private static boolean isWordAtPosition(CharSequence text, int start, int end, final String tokenText) {
    return CharArrayUtil.regionMatches(text, start, end, tokenText) && end - start == tokenText.length();
  }

}
