package com.jetbrains.python.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;

import static com.jetbrains.python.PyBundle.message;

/**
 * @author yole
 */
public class ExpressionParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#ru.yole.pythonlanguage.parsing.ExpressionParsing");

  public ExpressionParsing(ParsingContext context) {
    super(context);
  }

  public boolean parsePrimaryExpression(boolean isTargetExpression) {
    final IElementType firstToken = myBuilder.getTokenType();
    if (firstToken == PyTokenTypes.IDENTIFIER) {
      if (isTargetExpression) {
        buildTokenElement(PyElementTypes.TARGET_EXPRESSION, myBuilder);
      }
      else {
        buildTokenElement(PyElementTypes.REFERENCE_EXPRESSION, myBuilder);
      }
      return true;
    }
    else if (firstToken == PyTokenTypes.INTEGER_LITERAL) {
      buildTokenElement(PyElementTypes.INTEGER_LITERAL_EXPRESSION, myBuilder);
      return true;
    }
    else if (firstToken == PyTokenTypes.FLOAT_LITERAL) {
      buildTokenElement(PyElementTypes.FLOAT_LITERAL_EXPRESSION, myBuilder);
      return true;
    }
    else if (firstToken == PyTokenTypes.IMAGINARY_LITERAL) {
      buildTokenElement(PyElementTypes.IMAGINARY_LITERAL_EXPRESSION, myBuilder);
      return true;
    }
    else if (firstToken == PyTokenTypes.NONE_KEYWORD) {
      buildTokenElement(PyElementTypes.NONE_LITERAL_EXPRESSION, myBuilder);
      return true;
    }
    else if (firstToken == PyTokenTypes.TRUE_KEYWORD || firstToken == PyTokenTypes.FALSE_KEYWORD) {
      buildTokenElement(PyElementTypes.BOOL_LITERAL_EXPRESSION, myBuilder);
      return true;
    }
    else if (firstToken == PyTokenTypes.STRING_LITERAL) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      while (myBuilder.getTokenType() == PyTokenTypes.STRING_LITERAL) {
        myBuilder.advanceLexer();
      }
      marker.done(PyElementTypes.STRING_LITERAL_EXPRESSION);
      return true;
    }
    else if (firstToken == PyTokenTypes.LPAR) {
      parseParenthesizedExpression(isTargetExpression);
      return true;
    }
    else if (firstToken == PyTokenTypes.LBRACKET) {
      parseListLiteralExpression(myBuilder, isTargetExpression);
      return true;
    }
    else if (firstToken == PyTokenTypes.LBRACE) {
      parseDictOrSetDisplay();
      return true;
    }
    else if (firstToken == PyTokenTypes.TICK) {
      parseReprExpression(myBuilder);
      return true;
    }
    else if (firstToken == PyTokenTypes.DOT) {
      final PsiBuilder.Marker maybeEllipsis = myBuilder.mark();
      myBuilder.advanceLexer();
      if (matchToken(PyTokenTypes.DOT) && matchToken(PyTokenTypes.DOT)) {
        maybeEllipsis.done(PyElementTypes.NONE_LITERAL_EXPRESSION);
        return true;
      }
      maybeEllipsis.rollbackTo();
    }
    return false;
  }

  private void parseListLiteralExpression(final PsiBuilder builder, boolean isTargetExpression) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.LBRACKET);
    final PsiBuilder.Marker expr = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() == PyTokenTypes.RBRACKET) {
      builder.advanceLexer();
      expr.done(PyElementTypes.LIST_LITERAL_EXPRESSION);
      return;
    }
    if (!parseSingleExpression(isTargetExpression)) {
      builder.error(message("PARSE.expected.expression"));
    }
    if (builder.getTokenType() == PyTokenTypes.FOR_KEYWORD) {
      parseComprehension(expr, PyTokenTypes.RBRACKET, PyElementTypes.LIST_COMP_EXPRESSION, false);
    }
    else {
      while (builder.getTokenType() != PyTokenTypes.RBRACKET) {
        if (!matchToken(PyTokenTypes.COMMA)) {
          builder.error("expected ',' or ']'");
        }
        if (atToken(PyTokenTypes.RBRACKET)) {
          break;
        }
        if (!parseSingleExpression(isTargetExpression)) {
          builder.error(message("PARSE.expected.expr.or.comma.or.bracket"));
          break;
        }
      }
      checkMatches(PyTokenTypes.RBRACKET, message("PARSE.expected.rbracket"));
      expr.done(PyElementTypes.LIST_LITERAL_EXPRESSION);
    }
  }

  private void parseComprehension(PsiBuilder.Marker expr,
                                  final IElementType endToken,
                                  final IElementType exprType,
                                  final boolean leaveEndTokenOutside) {
    assertCurrentToken(PyTokenTypes.FOR_KEYWORD);
    while (true) {
      myBuilder.advanceLexer();
      parseExpression(true, true);
      checkMatches(PyTokenTypes.IN_KEYWORD, message("PARSE.expected.in"));
      if (!parseTupleExpression(false, false, true)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      while (myBuilder.getTokenType() == PyTokenTypes.IF_KEYWORD) {
        myBuilder.advanceLexer();
        parseOldExpression();
      }
      if (atToken(endToken)) {
        if (leaveEndTokenOutside) {
          expr.done(exprType);
          nextToken();
          return;
        }
        nextToken();
        break;
      }
      if (atToken(PyTokenTypes.FOR_KEYWORD)) {
        continue;
      }
      myBuilder.error(message("PARSE.expected.for.or.bracket"));
      break;
    }
    expr.done(exprType);
  }

  private void parseDictOrSetDisplay() {
    LOG.assertTrue(myBuilder.getTokenType() == PyTokenTypes.LBRACE);
    final PsiBuilder.Marker expr = myBuilder.mark();
    myBuilder.advanceLexer();

    if (matchToken(PyTokenTypes.RBRACE)) {
      expr.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
      return;
    }

    final PsiBuilder.Marker firstExprMarker = myBuilder.mark();
    if (!parseSingleExpression(false)) {
      myBuilder.error("expression expected");
      firstExprMarker.drop();
      expr.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
      return;
    }

    if (matchToken(PyTokenTypes.COLON)) {
      parseDictLiteralTail(expr, firstExprMarker);
    }
    else if (atToken(PyTokenTypes.COMMA) || atToken(PyTokenTypes.RBRACE)) {
      firstExprMarker.drop();
      parseSetLiteralTail(expr);
    }
    else if (atToken(PyTokenTypes.FOR_KEYWORD)) {
      firstExprMarker.drop();
      parseComprehension(expr, PyTokenTypes.RBRACE, PyElementTypes.SET_COMP_EXPRESSION, false);
    }
    else {
      myBuilder.error("expression expected");
      firstExprMarker.drop();
      expr.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
    }
  }

  private void parseDictLiteralTail(PsiBuilder.Marker startMarker, PsiBuilder.Marker firstKeyValueMarker) {
    if (!parseSingleExpression(false)) {
      myBuilder.error("expression expected");
      firstKeyValueMarker.done(PyElementTypes.KEY_VALUE_EXPRESSION);
      if (atToken(PyTokenTypes.RBRACE)) {
        myBuilder.advanceLexer();
      }
      startMarker.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
      return;
    }
    firstKeyValueMarker.done(PyElementTypes.KEY_VALUE_EXPRESSION);
    if (myBuilder.getTokenType() == PyTokenTypes.FOR_KEYWORD) {
      parseComprehension(startMarker, PyTokenTypes.RBRACE, PyElementTypes.DICT_COMP_EXPRESSION, false);
    }
    else {
      while (myBuilder.getTokenType() != PyTokenTypes.RBRACE) {
        checkMatches(PyTokenTypes.COMMA, message("PARSE.expected.comma"));
        if (!parseKeyValueExpression()) {
          break;
        }
      }
      myBuilder.advanceLexer();
      startMarker.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
    }
  }

  private boolean parseKeyValueExpression() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    if (!parseSingleExpression(false)) {
      marker.drop();
      return false;
    }
    checkMatches(PyTokenTypes.COLON, message("PARSE.expected.colon"));
    if (!parseSingleExpression(false)) {
      myBuilder.error("value expression expected");
      marker.drop();
      return false;
    }
    marker.done(PyElementTypes.KEY_VALUE_EXPRESSION);
    return true;
  }

  private void parseSetLiteralTail(PsiBuilder.Marker startMarker) {
    while (myBuilder.getTokenType() != PyTokenTypes.RBRACE) {
      checkMatches(PyTokenTypes.COMMA, message("PARSE.expected.comma"));
      if (!parseSingleExpression(false)) {
        break;
      }
    }
    myBuilder.advanceLexer();
    startMarker.done(PyElementTypes.SET_LITERAL_EXPRESSION);
  }

  private void parseParenthesizedExpression(boolean isTargetExpression) {
    LOG.assertTrue(myBuilder.getTokenType() == PyTokenTypes.LPAR);
    final PsiBuilder.Marker expr = myBuilder.mark();
    myBuilder.advanceLexer();
    if (myBuilder.getTokenType() == PyTokenTypes.RPAR) {
      myBuilder.advanceLexer();
      expr.done(PyElementTypes.TUPLE_EXPRESSION);
    }
    else {
      parseYieldOrTupleExpression(isTargetExpression);
      if (myBuilder.getTokenType() == PyTokenTypes.FOR_KEYWORD) {
        parseComprehension(expr, PyTokenTypes.RPAR, PyElementTypes.GENERATOR_EXPRESSION, false);
      }
      else {
        checkMatches(PyTokenTypes.RPAR, message("PARSE.expected.rpar"));
        expr.done(PyElementTypes.PARENTHESIZED_EXPRESSION);
      }
    }
  }

  private void parseReprExpression(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.TICK);
    final PsiBuilder.Marker expr = builder.mark();
    builder.advanceLexer();
    parseExpression();
    checkMatches(PyTokenTypes.TICK, message("PARSE.expected.tick"));
    expr.done(PyElementTypes.REPR_EXPRESSION);
  }

  public boolean parseMemberExpression(boolean isTargetExpression) {
    // in sequence a.b.... .c all members but last are always references, and the last may be target.
    boolean recast_first_identifier = false;
    boolean recast_qualifier = false;
    do {
      boolean first_identifier_is_target = isTargetExpression && ! recast_first_identifier;
      PsiBuilder.Marker expr = myBuilder.mark();
      if (!parsePrimaryExpression(first_identifier_is_target)) {
        expr.drop();
        return false;
      }

      while (true) {
        final IElementType tokenType = myBuilder.getTokenType();
        if (tokenType == PyTokenTypes.DOT) {
          if (first_identifier_is_target) {
            recast_first_identifier = true;
            expr.rollbackTo();
            break;
          }
          else recast_first_identifier = false; 
          myBuilder.advanceLexer();
          checkMatches(PyTokenTypes.IDENTIFIER, message("PARSE.expected.name"));
          if (isTargetExpression && ! recast_qualifier && myBuilder.getTokenType() != PyTokenTypes.DOT) {
            expr.done(PyElementTypes.TARGET_EXPRESSION);
          }
          else {
            expr.done(PyElementTypes.REFERENCE_EXPRESSION);
          }
          expr = expr.precede();
        }
        else if (tokenType == PyTokenTypes.LPAR) {
          parseArgumentList();
          expr.done(PyElementTypes.CALL_EXPRESSION);
          expr = expr.precede();
        }
        else if (tokenType == PyTokenTypes.LBRACKET) {
          myBuilder.advanceLexer();
          PsiBuilder.Marker sliceItemStart = myBuilder.mark();
          if (atToken(PyTokenTypes.COLON)) {
            PsiBuilder.Marker sliceMarker = myBuilder.mark();
            sliceMarker.done(PyElementTypes.EMPTY_EXPRESSION);
            parseSliceEnd(expr, sliceItemStart);
          }
          else {
            parseSingleExpression(false);
            if (atToken(PyTokenTypes.COLON)) {
              parseSliceEnd(expr, sliceItemStart);
            }
            else if (atToken(PyTokenTypes.COMMA)) {
              sliceItemStart.done(PyElementTypes.SLICE_ITEM);
              parseSliceListTail(expr);
            }
            else {
              sliceItemStart.drop();
              checkMatches(PyTokenTypes.RBRACKET, message("PARSE.expected.rbracket"));
              expr.done(PyElementTypes.SUBSCRIPTION_EXPRESSION);
              if (isTargetExpression && ! recast_qualifier) {
                recast_first_identifier = true; // subscription is always a reference
                recast_qualifier = true; // recast non-first qualifiers too
                expr.rollbackTo();
                break;
              }
            }
          }
          expr = expr.precede();
        }
        else {
          expr.drop();
          break;
        }
        recast_first_identifier = false; // it is true only after a break; normal flow always unsets it.
        // recast_qualifier is untouched, it remembers whether qualifiers were already recast 
      }
    }
    while (recast_first_identifier);

    return true;
  }

  private static TokenSet BRACKET_OR_COMMA = TokenSet.create(PyTokenTypes.RBRACKET, PyTokenTypes.COMMA);
  private static TokenSet BRACKET_COLON_COMMA = TokenSet.create(PyTokenTypes.RBRACKET, PyTokenTypes.COLON, PyTokenTypes.COMMA);

  private void parseSliceEnd(PsiBuilder.Marker exprStart, PsiBuilder.Marker sliceItemStart) {
    myBuilder.advanceLexer();
    if (atToken(PyTokenTypes.RBRACKET)) {
      PsiBuilder.Marker sliceMarker = myBuilder.mark();
      sliceMarker.done(PyElementTypes.EMPTY_EXPRESSION);
      sliceItemStart.done(PyElementTypes.SLICE_ITEM);
      nextToken();
      exprStart.done(PyElementTypes.SLICE_EXPRESSION);
      return;
    }
    else {
      if (atToken(PyTokenTypes.COLON)) {
        PsiBuilder.Marker sliceMarker = myBuilder.mark();
        sliceMarker.done(PyElementTypes.EMPTY_EXPRESSION);
      }
      else {
        parseSingleExpression(false);
      }
      if (!BRACKET_COLON_COMMA.contains(myBuilder.getTokenType())) {
        myBuilder.error(message("PARSE.expected.colon.or.rbracket"));
      }
      if (matchToken(PyTokenTypes.COLON)) {
        parseSingleExpression(false);
      }

      sliceItemStart.done(PyElementTypes.SLICE_ITEM);
      if (!BRACKET_OR_COMMA.contains(myBuilder.getTokenType())) {
        myBuilder.error("']' or ',' expected");
      }
    }

    parseSliceListTail(exprStart);
  }

  private void parseSliceListTail(PsiBuilder.Marker exprStart) {
    while (atToken(PyTokenTypes.COMMA)) {
      nextToken();
      PsiBuilder.Marker sliceItemStart = myBuilder.mark();
      parseTestExpression(false, false);
      if (matchToken(PyTokenTypes.COLON)) {
        parseTestExpression(false, false);
        if (matchToken(PyTokenTypes.COLON)) {
          parseTestExpression(false, false);
        }
      }
      sliceItemStart.done(PyElementTypes.SLICE_ITEM);
      if (!BRACKET_OR_COMMA.contains(myBuilder.getTokenType())) {
        myBuilder.error("']' or ',' expected");
        break;
      }
    }
    checkMatches(PyTokenTypes.RBRACKET, message("PARSE.expected.rbracket"));

    exprStart.done(PyElementTypes.SLICE_EXPRESSION);
  }

  public void parseArgumentList() {
    LOG.assertTrue(myBuilder.getTokenType() == PyTokenTypes.LPAR);
    final PsiBuilder.Marker arglist = myBuilder.mark();
    myBuilder.advanceLexer();
    final PsiBuilder.Marker genexpr = myBuilder.mark();
    int argNumber = 0;
    boolean needBracket = true;
    while (myBuilder.getTokenType() != PyTokenTypes.RPAR) {
      argNumber++;
      if (argNumber > 1) {
        if (argNumber == 2 && myBuilder.getTokenType() == PyTokenTypes.FOR_KEYWORD && genexpr != null) {
          parseComprehension(genexpr, PyTokenTypes.RPAR, PyElementTypes.GENERATOR_EXPRESSION, true);
          needBracket = false;
          break;
        }
        else if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
          myBuilder.advanceLexer();
          if (myBuilder.getTokenType() == PyTokenTypes.RPAR) {
            break;
          }
        }
        else {
          myBuilder.error(message("PARSE.expected.comma.or.rpar"));
          break;
        }
      }
      if (myBuilder.getTokenType() == PyTokenTypes.MULT || myBuilder.getTokenType() == PyTokenTypes.EXP) {
        final PsiBuilder.Marker starArgMarker = myBuilder.mark();
        myBuilder.advanceLexer();
        if (!parseSingleExpression(false)) {
          myBuilder.error(message("PARSE.expected.expression"));
        }
        starArgMarker.done(PyElementTypes.STAR_ARGUMENT_EXPRESSION);
      }
      else {
        if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
          final PsiBuilder.Marker keywordArgMarker = myBuilder.mark();
          myBuilder.advanceLexer();
          if (myBuilder.getTokenType() == PyTokenTypes.EQ) {
            myBuilder.advanceLexer();
            if (!parseSingleExpression(false)) {
              myBuilder.error(message("PARSE.expected.expression"));
            }
            keywordArgMarker.done(PyElementTypes.KEYWORD_ARGUMENT_EXPRESSION);
            continue;
          }
          keywordArgMarker.rollbackTo();
        }
        if (!parseSingleExpression(false)) {
          myBuilder.error(message("PARSE.expected.expression"));
          break;
        }
      }
    }

    if (needBracket) {
      if (genexpr != null) {
        genexpr.drop();
      }
      checkMatches(PyTokenTypes.RPAR, message("PARSE.expected.rpar"));
    }
    arglist.done(PyElementTypes.ARGUMENT_LIST);
  }

  public boolean parseExpressionOptional() {
    return parseTupleExpression(false, false, false);
  }

  public boolean parseExpressionOptional(boolean isTargetExpression) {
    return parseTupleExpression(false, isTargetExpression, false);
  }

  public void parseExpression() {
    if (!parseExpressionOptional()) {
      myBuilder.error(message("PARSE.expected.expression"));
    }
  }

  public void parseExpression(boolean stopOnIn, boolean isTargetExpression) {
    if (!parseTupleExpression(stopOnIn, isTargetExpression, false)) {
      myBuilder.error(message("PARSE.expected.expression"));
    }
  }

  public boolean parseYieldOrTupleExpression(final boolean isTargetExpression) {
    if (myBuilder.getTokenType() == PyTokenTypes.YIELD_KEYWORD) {
      PsiBuilder.Marker yieldExpr = myBuilder.mark();
      myBuilder.advanceLexer();
      parseTupleExpression(false, isTargetExpression, false);
      yieldExpr.done(PyElementTypes.YIELD_EXPRESSION);
      return true;
    }
    else {
      return parseTupleExpression(false, isTargetExpression, false);
    }
  }

  private boolean parseTupleExpression(boolean stopOnIn, boolean isTargetExpression, final boolean oldTest) {
    PsiBuilder.Marker expr = myBuilder.mark();
    boolean exprParseResult = oldTest ? parseOldTestExpression() : parseTestExpression(stopOnIn, isTargetExpression);
    if (!exprParseResult) {
      expr.drop();
      return false;
    }
    if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
      while (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
        myBuilder.advanceLexer();
        PsiBuilder.Marker expr2 = myBuilder.mark();
        exprParseResult = oldTest ? parseOldTestExpression() : parseTestExpression(stopOnIn, isTargetExpression);
        if (!exprParseResult) {
          expr2.rollbackTo();
          break;
        }
        expr2.drop();
      }
      expr.done(PyElementTypes.TUPLE_EXPRESSION);
    }
    else {
      expr.drop();
    }
    return true;
  }

  public boolean parseSingleExpression(boolean isTargetExpression) {
    return parseTestExpression(false, isTargetExpression);
  }

  public boolean parseOldExpression() {
    if (myBuilder.getTokenType() == PyTokenTypes.LAMBDA_KEYWORD) {
      return parseLambdaExpression(false);
    }
    return parseORTestExpression(false, false);
  }

  private boolean parseTestExpression(boolean stopOnIn, boolean isTargetExpression) {
    if (myBuilder.getTokenType() == PyTokenTypes.LAMBDA_KEYWORD) {
      return parseLambdaExpression(false);
    }
    PsiBuilder.Marker condExpr = myBuilder.mark();
    if (!parseORTestExpression( stopOnIn, isTargetExpression)) {
      condExpr.drop();
      return false;
    }
    if (myBuilder.getTokenType() == PyTokenTypes.IF_KEYWORD) {
      myBuilder.advanceLexer();
      if (!parseORTestExpression(stopOnIn, isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      else {
        if (myBuilder.getTokenType() != PyTokenTypes.ELSE_KEYWORD) {
          myBuilder.error(message("PARSE.expected.else"));
        }
        else {
          myBuilder.advanceLexer();
          if (!parseTestExpression(stopOnIn, isTargetExpression)) {
            myBuilder.error(message("PARSE.expected.expression"));
          }
        }
      }
      condExpr.done(PyElementTypes.CONDITIONAL_EXPRESSION);
    }
    else {
      condExpr.drop();
    }
    return true;
  }

  private boolean parseOldTestExpression() {
    if (myBuilder.getTokenType() == PyTokenTypes.LAMBDA_KEYWORD) {
      return parseLambdaExpression(true);
    }
    return parseORTestExpression(false, false);
  }

  private boolean parseLambdaExpression(final boolean oldTest) {
    PsiBuilder.Marker expr = myBuilder.mark();
    myBuilder.advanceLexer();
    getFunctionParser().parseParameterListContents(PyTokenTypes.COLON, false, true);
    boolean parseExpressionResult = oldTest ? parseOldTestExpression() : parseSingleExpression(false);
    if (!parseExpressionResult) {
      myBuilder.error(message("PARSE.expected.expression"));
    }
    expr.done(PyElementTypes.LAMBDA_EXPRESSION);
    return true;
  }

  private boolean parseORTestExpression(boolean stopOnIn, boolean isTargetExpression) {
    PsiBuilder.Marker expr = myBuilder.mark();
    if (!parseANDTestExpression(stopOnIn, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (myBuilder.getTokenType() == PyTokenTypes.OR_KEYWORD) {
      myBuilder.advanceLexer();
      if (!parseANDTestExpression(stopOnIn, isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseANDTestExpression(boolean stopOnIn, boolean isTargetExpression) {
    PsiBuilder.Marker expr = myBuilder.mark();
    if (!parseNOTTestExpression(stopOnIn, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (myBuilder.getTokenType() == PyTokenTypes.AND_KEYWORD) {
      myBuilder.advanceLexer();
      if (!parseNOTTestExpression(stopOnIn, isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseNOTTestExpression(boolean stopOnIn, boolean isTargetExpression) {
    if (myBuilder.getTokenType() == PyTokenTypes.NOT_KEYWORD) {
      final PsiBuilder.Marker expr = myBuilder.mark();
      myBuilder.advanceLexer();
      if (!parseNOTTestExpression(stopOnIn, isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.PREFIX_EXPRESSION);
      return true;
    }
    else {
      return parseComparisonExpression(stopOnIn, isTargetExpression);
    }
  }

  private boolean parseComparisonExpression(boolean stopOnIn, boolean isTargetExpression) {
    PsiBuilder.Marker expr = myBuilder.mark();
    if (!parseStarExpression(isTargetExpression)) {
      expr.drop();
      return false;
    }
    if (stopOnIn && atToken(PyTokenTypes.IN_KEYWORD)) {
      expr.drop();
      return true;
    }
    while (PyTokenTypes.COMPARISON_OPERATIONS.contains(myBuilder.getTokenType())) {
      if (atToken(PyTokenTypes.NOT_KEYWORD)) {
        PsiBuilder.Marker notMarker = myBuilder.mark();
        myBuilder.advanceLexer();
        if (!atToken(PyTokenTypes.IN_KEYWORD)) {
          notMarker.rollbackTo();
          break;
        }
        notMarker.drop();
        myBuilder.advanceLexer();
      }
      else if (atToken(PyTokenTypes.IS_KEYWORD)) {
        myBuilder.advanceLexer();
        if (myBuilder.getTokenType() == PyTokenTypes.NOT_KEYWORD) {
          myBuilder.advanceLexer();
        }
      }
      else {
        myBuilder.advanceLexer();
      }

      if (!parseBitwiseORExpression(isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseStarExpression(boolean isTargetExpression) {
    if (atToken(PyTokenTypes.MULT)) {
      PsiBuilder.Marker starExpr = myBuilder.mark();
      nextToken();
      if (!parseBitwiseORExpression(isTargetExpression)) {
        starExpr.drop();
        return false;
      }
      starExpr.done(PyElementTypes.STAR_EXPRESSION);
      return true;
    }
    return parseBitwiseORExpression(isTargetExpression);
  }

  private boolean parseBitwiseORExpression(boolean isTargetExpression) {
    PsiBuilder.Marker expr = myBuilder.mark();
    if (!parseBitwiseXORExpression(isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (atToken(PyTokenTypes.OR)) {
      myBuilder.advanceLexer();
      if (!parseBitwiseXORExpression(isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseBitwiseXORExpression(boolean isTargetExpression) {
    PsiBuilder.Marker expr = myBuilder.mark();
    if (!parseBitwiseANDExpression(isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (atToken(PyTokenTypes.XOR)) {
      myBuilder.advanceLexer();
      if (!parseBitwiseANDExpression(isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseBitwiseANDExpression(boolean isTargetExpression) {
    PsiBuilder.Marker expr = myBuilder.mark();
    if (!parseShiftExpression(isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (atToken(PyTokenTypes.AND)) {
      myBuilder.advanceLexer();
      if (!parseShiftExpression(isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseShiftExpression(boolean isTargetExpression) {
    PsiBuilder.Marker expr = myBuilder.mark();
    if (!parseAdditiveExpression(myBuilder, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (PyTokenTypes.SHIFT_OPERATIONS.contains(myBuilder.getTokenType())) {
      myBuilder.advanceLexer();
      if (!parseAdditiveExpression(myBuilder, isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseAdditiveExpression(final PsiBuilder myBuilder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = myBuilder.mark();
    if (!parseMultiplicativeExpression(isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (PyTokenTypes.ADDITIVE_OPERATIONS.contains(myBuilder.getTokenType())) {
      myBuilder.advanceLexer();
      if (!parseMultiplicativeExpression(isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseMultiplicativeExpression(boolean isTargetExpression) {
    PsiBuilder.Marker expr = myBuilder.mark();
    if (!parseUnaryExpression(isTargetExpression)) {
      expr.drop();
      return false;
    }

    while (PyTokenTypes.MULTIPLICATIVE_OPERATIONS.contains(myBuilder.getTokenType())) {
      myBuilder.advanceLexer();
      if (!parseUnaryExpression(isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseUnaryExpression(boolean isTargetExpression) {
    final IElementType tokenType = myBuilder.getTokenType();
    if (PyTokenTypes.UNARY_OPERATIONS.contains(tokenType)) {
      final PsiBuilder.Marker expr = myBuilder.mark();
      myBuilder.advanceLexer();
      if (!parseUnaryExpression(isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.PREFIX_EXPRESSION);
      return true;
    }
    else {
      return parsePowerExpression(isTargetExpression);
    }
  }

  private boolean parsePowerExpression(boolean isTargetExpression) {
    PsiBuilder.Marker expr = myBuilder.mark();
    if (!parseMemberExpression(isTargetExpression)) {
      expr.drop();
      return false;
    }

    if (myBuilder.getTokenType() == PyTokenTypes.EXP) {
      myBuilder.advanceLexer();
      if (!parseUnaryExpression(isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
    }
    else {
      expr.drop();
    }

    return true;
  }

  private static void buildTokenElement(IElementType type, PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    marker.done(type);
  }
}
