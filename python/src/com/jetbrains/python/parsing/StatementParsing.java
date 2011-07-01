package com.jetbrains.python.parsing;

import com.intellij.lang.ITokenTypeRemapper;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;


/**
 * @author yole
 */
public class StatementParsing extends Parsing implements ITokenTypeRemapper {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.parsing.StatementParsing");
  @NonNls protected static final String TOK_FUTURE_IMPORT = "__future__";
  @NonNls protected static final String TOK_WITH_STATEMENT = "with_statement";
  @NonNls protected static final String TOK_NESTED_SCOPES = "nested_scopes";
  @NonNls protected static final String TOK_PRINT_FUNCTION = "print_function";
  @NonNls protected static final String TOK_WITH = "with";
  @NonNls protected static final String TOK_AS = "as";
  @NonNls protected static final String TOK_PRINT = "print";
  @NonNls protected static final String TOK_NONE = "None";
  @NonNls protected static final String TOK_TRUE = "True";
  @NonNls protected static final String TOK_FALSE = "False";
  @NonNls protected static final String TOK_NONLOCAL = "nonlocal";
  @NonNls protected static final String TOK_EXEC = "exec";

  protected enum Phase {NONE, FROM, FUTURE, IMPORT} // 'from __future__ import' phase
  private Phase myFutureImportPhase = Phase.NONE;
  private boolean myExpectAsKeyword = false;

  public enum FUTURE {ABSOLUTE_IMPORT, DIVISION, GENERATORS, NESTED_SCOPES, WITH_STATEMENT, PRINT_FUNCTION}
  protected Set<FUTURE> myFutureFlags = EnumSet.noneOf(FUTURE.class);

  protected StatementParsing(ParsingContext context, @Nullable FUTURE futureFlag) {
    super(context);
    if (futureFlag != null) {
      myFutureFlags.add(futureFlag);
    }
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
      parseIfStatement();
      return;
    }
    if (firstToken == PyTokenTypes.FOR_KEYWORD) {
      parseForStatement();
      return;
    }
    if (firstToken == PyTokenTypes.TRY_KEYWORD) {
      parseTryStatement();
      return;
    }
    if (firstToken == PyTokenTypes.DEF_KEYWORD) {
      getFunctionParser().parseFunctionDeclaration();
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
    if (firstToken == PyTokenTypes.PRINT_KEYWORD && hasPrintStatement()) {
      parsePrintStatement(builder, inSuite);
      return;
    }
    if (firstToken == PyTokenTypes.ASSERT_KEYWORD) {
      parseAssertStatement(inSuite);
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
      parseDelStatement(inSuite);
      return;
    }
    if (firstToken == PyTokenTypes.EXEC_KEYWORD) {
      parseExecStatement(inSuite);
      return;
    }
    if (firstToken == PyTokenTypes.GLOBAL_KEYWORD) {
      parseNameDefiningStatement(inSuite, PyElementTypes.GLOBAL_STATEMENT);
      return;
    }
    if (firstToken == PyTokenTypes.NONLOCAL_KEYWORD) {
      parseNameDefiningStatement(inSuite, PyElementTypes.NONLOCAL_STATEMENT);
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
      parseRaiseStatement(inSuite);
      return;
    }
    PsiBuilder.Marker exprStatement = builder.mark();
    if (builder.getTokenType() == PyTokenTypes.YIELD_KEYWORD) {
      getExpressionParser().parseYieldOrTupleExpression(false);
      checkEndOfStatement(inSuite);
      exprStatement.done(PyElementTypes.EXPRESSION_STATEMENT);
      return;
    }
    else if (getExpressionParser().parseExpressionOptional()) {
      IElementType statementType = PyElementTypes.EXPRESSION_STATEMENT;
      if (PyTokenTypes.AUG_ASSIGN_OPERATIONS.contains(builder.getTokenType())) {
        statementType = PyElementTypes.AUG_ASSIGNMENT_STATEMENT;
        builder.advanceLexer();
        if (!getExpressionParser().parseYieldOrTupleExpression(false)) {
          builder.error("expression expected");          
        }
      }
      else if (builder.getTokenType() == PyTokenTypes.EQ) {
        statementType = PyElementTypes.ASSIGNMENT_STATEMENT;
        exprStatement.rollbackTo();
        exprStatement = builder.mark();
        getExpressionParser().parseExpression(false, true);
        LOG.assertTrue(builder.getTokenType() == PyTokenTypes.EQ);
        builder.advanceLexer();

        while (true) {
          PsiBuilder.Marker maybeExprMarker = builder.mark();
          if (!getExpressionParser().parseYieldOrTupleExpression(false)) {
            maybeExprMarker.drop();
            builder.error("expression expected");
            break;
          }
          if (builder.getTokenType() == PyTokenTypes.EQ) {
            maybeExprMarker.rollbackTo();
            getExpressionParser().parseExpression(false, true);
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
    if (firstToken == PyTokenTypes.INCONSISTENT_DEDENT) {
      builder.error("unindent does not match any outer indentation level");
    }
    else {
      builder.error("statement expected, found " + firstToken.toString());
    }
  }

  private boolean hasPrintStatement() {
    return myContext.getLanguageLevel().hasPrintStatement() && !myFutureFlags.contains(FUTURE.PRINT_FUNCTION);
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
    if (builder.getTokenType() != null && !PyTokenTypes.END_OF_STATEMENT.contains(builder.getTokenType())) {
      getExpressionParser().parseExpression();
    }
    checkEndOfStatement(inSuite);
    returnStatement.done(PyElementTypes.RETURN_STATEMENT);
  }

  private void parseDelStatement(boolean inSuite) {
    assertCurrentToken(PyTokenTypes.DEL_KEYWORD);
    final PsiBuilder.Marker delStatement = myBuilder.mark();
    myBuilder.advanceLexer();
    if (!getExpressionParser().parseSingleExpression(false)) {
      myBuilder.error("expression expected");
    }
    while (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
      myBuilder.advanceLexer();
      if (!PyTokenTypes.END_OF_STATEMENT.contains(myBuilder.getTokenType())) {
        if (!getExpressionParser().parseSingleExpression(false)) {
          myBuilder.error("expression expected");
        }
      }
    }

    checkEndOfStatement(inSuite);
    delStatement.done(PyElementTypes.DEL_STATEMENT);
  }

  private void parseRaiseStatement(boolean inSuite) {
    assertCurrentToken(PyTokenTypes.RAISE_KEYWORD);
    final PsiBuilder.Marker raiseStatement = myBuilder.mark();
    myBuilder.advanceLexer();
    if (!PyTokenTypes.END_OF_STATEMENT.contains(myBuilder.getTokenType())) {
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
        getExpressionParser().parseSingleExpression(false);
      }
    }
    checkEndOfStatement(inSuite);
    raiseStatement.done(PyElementTypes.RAISE_STATEMENT);
  }

  private void parseAssertStatement(boolean inSuite) {
    assertCurrentToken(PyTokenTypes.ASSERT_KEYWORD);
    final PsiBuilder.Marker assertStatement = myBuilder.mark();
    myBuilder.advanceLexer();
    if (getExpressionParser().parseSingleExpression(false)) {
      if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
        myBuilder.advanceLexer();
        if (!getExpressionParser().parseSingleExpression(false)) {
          myContext.getBuilder().error("Expression expected");
        }
      }
      checkEndOfStatement(inSuite);
    }
    else myContext.getBuilder().error("Expression expected");
    assertStatement.done(PyElementTypes.ASSERT_STATEMENT);
  }

  private void parseImportStatement(boolean inSuite) {
    PsiBuilder builder = myContext.getBuilder();
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.IMPORT_KEYWORD);
    final PsiBuilder.Marker importStatement = builder.mark();
    builder.advanceLexer();
    parseImportElements(true, false, false);
    checkEndOfStatement(inSuite);
    importStatement.done(PyElementTypes.IMPORT_STATEMENT);
  }

  /*
  Really parses two forms:
  from identifier import id, id... -- may be either relative or absolute
  from . import identifier -- only relative
   */
  private void parseFromImportStatement(boolean inSuite) {
    PsiBuilder builder = myContext.getBuilder();
    assertCurrentToken(PyTokenTypes.FROM_KEYWORD);
    myFutureImportPhase = Phase.FROM;
    final PsiBuilder.Marker fromImportStatement = builder.mark();
    builder.advanceLexer();
    boolean from_future = false;
    boolean had_dots = parseRelativeImportDots();
    if (had_dots && parseOptionalDottedName() || parseDottedName()) {
      checkMatches(PyTokenTypes.IMPORT_KEYWORD, "'import' expected");
      if (myFutureImportPhase == Phase.FUTURE) {
        myFutureImportPhase = Phase.IMPORT;
        from_future = true;
      }
      if (builder.getTokenType() == PyTokenTypes.MULT) {
        final PsiBuilder.Marker star_import_mark = builder.mark();
        builder.advanceLexer();
        star_import_mark.done(PyElementTypes.STAR_IMPORT_ELEMENT);
      }
      else if (builder.getTokenType() == PyTokenTypes.LPAR) {
        builder.advanceLexer();
        parseImportElements(false, true, from_future);
        checkMatches(PyTokenTypes.RPAR, ") expected");
      }
      else {
        parseImportElements(false, false, from_future);
      }
    }
    else if (had_dots) { // from . import ...
      checkMatches(PyTokenTypes.IMPORT_KEYWORD, "'import' expected");
      parseImportElements(false, false, from_future);
    }
    checkEndOfStatement(inSuite);
    fromImportStatement.done(PyElementTypes.FROM_IMPORT_STATEMENT);
    myFutureImportPhase = Phase.NONE;
  }

  /**
   * Parses option dots before imported name.
   * @return true iff there were dots.
   */
  private boolean parseRelativeImportDots() {
    PsiBuilder builder = myContext.getBuilder();
    boolean had_dots = false;
    while (builder.getTokenType() == PyTokenTypes.DOT) {
      had_dots = true;
      builder.advanceLexer();
    }
    return had_dots;
  }

  private void parseImportElements(boolean is_module_import, boolean in_parens, final boolean from_future) {
    PsiBuilder builder = myContext.getBuilder();
    while (true) {
      final PsiBuilder.Marker asMarker = builder.mark();
      if (is_module_import) { // import _
        if (!parseDottedNameAsAware(true, false)) {
          asMarker.drop(); 
          break;
        }
      }
      else { // from X import _
        String token_text = parseIdentifier(PyElementTypes.REFERENCE_EXPRESSION);
        if (from_future) {
          // TODO: mark all known future feature names
          if (TOK_WITH_STATEMENT.equals(token_text)) {
            myFutureFlags.add(FUTURE.WITH_STATEMENT);
          }
          else if (TOK_NESTED_SCOPES.equals(token_text)) {
            myFutureFlags.add(FUTURE.NESTED_SCOPES);
          }
          else if (TOK_PRINT_FUNCTION.equals(token_text)) {
            myFutureFlags.add(FUTURE.PRINT_FUNCTION);
          }
        }
      }
      myExpectAsKeyword = true; // possible 'as' comes as an ident; reparse it as keyword if found
      if (builder.getTokenType() == PyTokenTypes.AS_KEYWORD) {
        builder.advanceLexer();
        myExpectAsKeyword = false;
        parseIdentifier(PyElementTypes.TARGET_EXPRESSION);
      }
      asMarker.done(PyElementTypes.IMPORT_ELEMENT);
      myExpectAsKeyword = false;
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

  @Nullable
  private String parseIdentifier(final IElementType elementType) {
    final PsiBuilder.Marker idMarker = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
      String id_text = myBuilder.getTokenText();
      myBuilder.advanceLexer();
      idMarker.done(elementType);
      return id_text;
    }
    else {
      myBuilder.error("identifier expected");
      idMarker.drop();
    }
    return null;
  }

  public boolean parseOptionalDottedName() {
    return parseDottedNameAsAware(false, true);
  }

  public boolean parseDottedName() {
    return parseDottedNameAsAware(false, false);
  }

  // true if identifier was parsed or skipped as optional, false on error
  protected boolean parseDottedNameAsAware(boolean expect_as, boolean optional) {
    if (myBuilder.getTokenType() != PyTokenTypes.IDENTIFIER) {
      if (optional) return true;
      myBuilder.error("identifier expected");
      return false;
    }
    PsiBuilder.Marker marker = myBuilder.mark();
    myBuilder.advanceLexer();
    marker.done(PyElementTypes.REFERENCE_EXPRESSION);
    boolean old_expect_AS_kwd = myExpectAsKeyword;
    myExpectAsKeyword = expect_as;
    while (myBuilder.getTokenType() == PyTokenTypes.DOT) {
      marker = marker.precede();
      myBuilder.advanceLexer();
      checkMatches(PyTokenTypes.IDENTIFIER, "identifier expected");
      marker.done(PyElementTypes.REFERENCE_EXPRESSION);
    }
    myExpectAsKeyword = old_expect_AS_kwd;
    return true;
  }

  private void parseNameDefiningStatement(boolean inSuite, final PyElementType elementType) {
    final PsiBuilder.Marker globalStatement = myBuilder.mark();
    myBuilder.advanceLexer();
    parseIdentifier(PyElementTypes.TARGET_EXPRESSION);
    while (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
      myBuilder.advanceLexer();
      parseIdentifier(PyElementTypes.TARGET_EXPRESSION);
    }
    checkEndOfStatement(inSuite);
    globalStatement.done(elementType);
  }

  private void parseExecStatement(boolean inSuite) {
    assertCurrentToken(PyTokenTypes.EXEC_KEYWORD);
    final PsiBuilder.Marker execStatement = myBuilder.mark();
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
    checkEndOfStatement(inSuite);
    execStatement.done(PyElementTypes.EXEC_STATEMENT);
  }

  private void parseIfStatement() {
    assertCurrentToken(PyTokenTypes.IF_KEYWORD);
    final PsiBuilder.Marker ifStatement = myBuilder.mark();
    final PsiBuilder.Marker ifPart = myBuilder.mark();
    myBuilder.advanceLexer();
    getExpressionParser().parseExpression();
    if (checkMatches(PyTokenTypes.COLON, "colon expected")) {
      parseSuite();
    }
    ifPart.done(PyElementTypes.IF_PART_IF);
    PsiBuilder.Marker elifPart = myBuilder.mark();
    while (myBuilder.getTokenType() == PyTokenTypes.ELIF_KEYWORD) {
      myBuilder.advanceLexer();
      getExpressionParser().parseExpression();
      checkMatches(PyTokenTypes.COLON, "colon expected");
      parseSuite();
      elifPart.done(PyElementTypes.IF_PART_ELIF);
      elifPart = myBuilder.mark();
    }
    elifPart.drop(); // we always kept an open extra elif
    final PsiBuilder.Marker elsePart = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
      myBuilder.advanceLexer();
      checkMatches(PyTokenTypes.COLON, "colon expected");
      parseSuite();
      elsePart.done(PyElementTypes.ELSE_PART);
    }
    else elsePart.drop();
    ifStatement.done(PyElementTypes.IF_STATEMENT);
  }

  private void parseForStatement() {
    assertCurrentToken(PyTokenTypes.FOR_KEYWORD);
    final PsiBuilder.Marker statement = myBuilder.mark();
    final PsiBuilder.Marker forPart = myBuilder.mark();
    myBuilder.advanceLexer();
    getExpressionParser().parseExpression(true, true);
    checkMatches(PyTokenTypes.IN_KEYWORD, "'in' expected");
    getExpressionParser().parseExpression();
    checkMatches(PyTokenTypes.COLON, "colon expected");
    parseSuite();
    forPart.done(PyElementTypes.FOR_PART);
    final PsiBuilder.Marker elsePart = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
      myBuilder.advanceLexer();
      checkMatches(PyTokenTypes.COLON, "colon expected");
      parseSuite();
      elsePart.done(PyElementTypes.ELSE_PART);
    }
    else elsePart.drop();
    statement.done(PyElementTypes.FOR_STATEMENT);
  }

  private void parseWhileStatement() {
    assertCurrentToken(PyTokenTypes.WHILE_KEYWORD);
    final PsiBuilder.Marker statement = myBuilder.mark();
    final PsiBuilder.Marker whilePart = myBuilder.mark();
    myBuilder.advanceLexer();
    if (!getExpressionParser().parseSingleExpression(false)) {
      myBuilder.error("expression expected");
    }
    checkMatches(PyTokenTypes.COLON, "colon expected");
    parseSuite();
    whilePart.done(PyElementTypes.WHILE_PART);
    final PsiBuilder.Marker elsePart = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
      myBuilder.advanceLexer();
      checkMatches(PyTokenTypes.COLON, "colon expected");
      parseSuite();
      elsePart.done(PyElementTypes.ELSE_PART);
    }
    else elsePart.drop();
    statement.done(PyElementTypes.WHILE_STATEMENT);
  }

  private void parseTryStatement() {
    assertCurrentToken(PyTokenTypes.TRY_KEYWORD);
    final PsiBuilder.Marker statement = myBuilder.mark();
    final PsiBuilder.Marker tryPart = myBuilder.mark();
    myBuilder.advanceLexer();
    checkMatches(PyTokenTypes.COLON, "colon expected");
    parseSuite();
    tryPart.done(PyElementTypes.TRY_PART);
    boolean haveExceptClause = false;
    if (myBuilder.getTokenType() == PyTokenTypes.EXCEPT_KEYWORD) {
      haveExceptClause = true;
      while (myBuilder.getTokenType() == PyTokenTypes.EXCEPT_KEYWORD) {
        final PsiBuilder.Marker exceptBlock = myBuilder.mark();
        myBuilder.advanceLexer();
        if (myBuilder.getTokenType() != PyTokenTypes.COLON) {
          if (!getExpressionParser().parseSingleExpression(false)) {
            myBuilder.error("expression expected");
          }
          myExpectAsKeyword = true;
          if (myBuilder.getTokenType() == PyTokenTypes.COMMA || myBuilder.getTokenType() == PyTokenTypes.AS_KEYWORD) {
            myBuilder.advanceLexer();
            if (!getExpressionParser().parseSingleExpression(true)) {
              myBuilder.error("expression expected");
            }
          }
        }
        checkMatches(PyTokenTypes.COLON, "colon expected");
        parseSuite();
        exceptBlock.done(PyElementTypes.EXCEPT_PART);
      }
      final PsiBuilder.Marker elsePart = myBuilder.mark();
      if (myBuilder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
        myBuilder.advanceLexer();
        checkMatches(PyTokenTypes.COLON, "colon expected");
        parseSuite();
        elsePart.done(PyElementTypes.ELSE_PART);
      }
      else elsePart.drop();
    }
    final PsiBuilder.Marker finallyPart = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.FINALLY_KEYWORD) {
      myBuilder.advanceLexer();
      checkMatches(PyTokenTypes.COLON, "colon expected");
      parseSuite();
      finallyPart.done(PyElementTypes.FINALLY_PART);
    }
    else {
      finallyPart.drop();
      if (!haveExceptClause) {
        myBuilder.error("'except' or 'finally' expected");
        // much better to have a statement of incorrectly determined type
        // than "TRY" and "COLON" tokens attached to nothing
      }
    }
    statement.done(PyElementTypes.TRY_EXCEPT_STATEMENT);
  }

  private void parseWithStatement() {
    assertCurrentToken(PyTokenTypes.WITH_KEYWORD);
    final PsiBuilder.Marker statement = myBuilder.mark();
    myBuilder.advanceLexer();
    while(true) {
      PsiBuilder.Marker withItem = myBuilder.mark();
      getExpressionParser().parseExpression();
      myExpectAsKeyword = true;
      if (myBuilder.getTokenType() == PyTokenTypes.AS_KEYWORD) {
        myBuilder.advanceLexer();
        getExpressionParser().parseSingleExpression(true); // 'as' is followed by a target
      }
      withItem.done(PyElementTypes.WITH_ITEM);
      if (!matchToken(PyTokenTypes.COMMA)) {
        break;
      }
    }
    checkMatches(PyTokenTypes.COLON, "colon expected");
    parseSuite();
    statement.done(PyElementTypes.WITH_STATEMENT);
  }

  private void parseClassDeclaration() {
    final PsiBuilder.Marker classMarker = myBuilder.mark();
    parseClassDeclaration(classMarker);
  }

  public void parseClassDeclaration(PsiBuilder.Marker classMarker) {
    assertCurrentToken(PyTokenTypes.CLASS_KEYWORD);
    myBuilder.advanceLexer();
    checkMatches(PyTokenTypes.IDENTIFIER, "identifier expected");
    if (myBuilder.getTokenType() == PyTokenTypes.LPAR) {
      getExpressionParser().parseArgumentList();
    }
    else {
      final PsiBuilder.Marker inheritMarker = myBuilder.mark();
      inheritMarker.done(PyElementTypes.ARGUMENT_LIST);
    }
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
        if (myBuilder.eof()) {
          myBuilder.error("indented block expected");
        }
        else {
          while (!myBuilder.eof() && myBuilder.getTokenType() != PyTokenTypes.DEDENT) {
            parseStatement();
          }
        }
      }

      marker.done(PyElementTypes.STATEMENT_LIST);
      marker.setCustomEdgeTokenBinders(null, FollowingCommentBinder.INSTANCE);
      if (endMarker != null) {
        endMarker.done(elType);
      }
      if (!myBuilder.eof()) {
        checkMatches(PyTokenTypes.DEDENT, "dedent expected");
      }
      // NOTE: the following line advances the PsiBuilder lexer and thus
      // ensures that the whitespace following the statement list is included
      // in the block containing the statement list
      myBuilder.getTokenType();
    }
    else {
      final PsiBuilder.Marker marker = myBuilder.mark();
      if (myBuilder.eof()) {
        myBuilder.error("statement expected");
      }
      else {
        parseSimpleStatement(true);
        while (matchToken(PyTokenTypes.SEMICOLON)) {
          if (matchToken(PyTokenTypes.STATEMENT_BREAK))
            break;
          parseSimpleStatement(true);
        }
      }
      marker.done(PyElementTypes.STATEMENT_LIST);
      if (endMarker != null) {
        endMarker.done(elType);
      }
    }
  }
  public IElementType filter(final IElementType source, final int start, final int end, final CharSequence text) {
    if (
      (myExpectAsKeyword || myContext.getLanguageLevel().hasWithStatement()) &&
      source == PyTokenTypes.IDENTIFIER && isWordAtPosition(text, start, end, TOK_AS)
    ) {
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
    else if (
        hasWithStatement() &&
        source == PyTokenTypes.IDENTIFIER &&
        isWordAtPosition(text, start, end, TOK_WITH)
    ) {
      return PyTokenTypes.WITH_KEYWORD;
    }
    else if (hasPrintStatement() && source == PyTokenTypes.IDENTIFIER &&
             isWordAtPosition(text, start, end, TOK_PRINT)) {
      return PyTokenTypes.PRINT_KEYWORD;
    }
    else if (myContext.getLanguageLevel().isPy3K() && source == PyTokenTypes.IDENTIFIER) {
      if (isWordAtPosition(text, start, end, TOK_NONE)) {
        return PyTokenTypes.NONE_KEYWORD;
      }
      if (isWordAtPosition(text, start, end, TOK_TRUE)) {
        return PyTokenTypes.TRUE_KEYWORD;
      }
      if (isWordAtPosition(text, start, end, TOK_FALSE)) {
        return PyTokenTypes.FALSE_KEYWORD;
      }
      if (isWordAtPosition(text, start, end, TOK_NONLOCAL)) {
        return PyTokenTypes.NONLOCAL_KEYWORD;
      }
    }
    else if (!myContext.getLanguageLevel().isPy3K() && source == PyTokenTypes.IDENTIFIER) {
      if (isWordAtPosition(text, start, end, TOK_EXEC)) {
        return PyTokenTypes.EXEC_KEYWORD;
      }
    }
    return source;
  }

  private static boolean isWordAtPosition(CharSequence text, int start, int end, final String tokenText) {
    return CharArrayUtil.regionMatches(text, start, end, tokenText) && end - start == tokenText.length();
  }

  private boolean hasWithStatement() {
    return myContext.getLanguageLevel().hasWithStatement() || myFutureFlags.contains(FUTURE.WITH_STATEMENT);
  }
}
