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
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 10:26:06
 * To change this template use File | Settings | File Templates.
 */
public class ExpressionParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#ru.yole.pythonlanguage.parsing.ExpressionParsing");

  public ExpressionParsing(ParsingContext context) {
    super(context);
  }

  public boolean parsePrimaryExpression(PsiBuilder builder, boolean isTargetExpression) {
    final IElementType firstToken = builder.getTokenType();
    if (firstToken == PyTokenTypes.IDENTIFIER) {
      if (isTargetExpression) {
        buildTokenElement(PyElementTypes.TARGET_EXPRESSION, builder);
      }
      else {
        buildTokenElement(PyElementTypes.REFERENCE_EXPRESSION, builder);
      }
      return true;
    }
    else if (firstToken == PyTokenTypes.INTEGER_LITERAL) {
      buildTokenElement(PyElementTypes.INTEGER_LITERAL_EXPRESSION, builder);
      return true;
    }
    else if (firstToken == PyTokenTypes.FLOAT_LITERAL) {
      buildTokenElement(PyElementTypes.FLOAT_LITERAL_EXPRESSION, builder);
      return true;
    }
    else if (firstToken == PyTokenTypes.IMAGINARY_LITERAL) {
      buildTokenElement(PyElementTypes.IMAGINARY_LITERAL_EXPRESSION, builder);
      return true;
    }
    else if (firstToken == PyTokenTypes.STRING_LITERAL) {
      final PsiBuilder.Marker marker = builder.mark();
      while (builder.getTokenType() == PyTokenTypes.STRING_LITERAL) {
        builder.advanceLexer();
      }
      marker.done(PyElementTypes.STRING_LITERAL_EXPRESSION);
      return true;
    }
    else if (firstToken == PyTokenTypes.LPAR) {
      parseParenthesizedExpression(builder, isTargetExpression);
      return true;
    }
    else if (firstToken == PyTokenTypes.LBRACKET) {
      parseListLiteralExpression(builder, isTargetExpression);
      return true;
    }
    else if (firstToken == PyTokenTypes.LBRACE) {
      parseDictLiteralExpression(builder);
      return true;
    }
    else if (firstToken == PyTokenTypes.TICK) {
      parseReprExpression(builder);
      return true;
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
    if (!parseSingleExpression(builder, isTargetExpression)) {
      builder.error("expression expected");
    }
    if (builder.getTokenType() == PyTokenTypes.FOR_KEYWORD) {
      parseListCompExpression(builder, expr, PyTokenTypes.RBRACKET, PyElementTypes.LIST_COMP_EXPRESSION);
    }
    else {
      while (builder.getTokenType() != PyTokenTypes.RBRACKET) {
        if (builder.getTokenType() == PyTokenTypes.COMMA) {
          builder.advanceLexer();
        }
        else if (!parseSingleExpression(builder, isTargetExpression)) {
          builder.error("expression or , or ] expected");
          break;
        }
      }
      checkMatches(PyTokenTypes.RBRACKET, "] expected");
      expr.done(PyElementTypes.LIST_LITERAL_EXPRESSION);
    }
  }

  private void parseListCompExpression(final PsiBuilder builder,
                                       PsiBuilder.Marker expr,
                                       final IElementType endToken,
                                       final IElementType exprType) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.FOR_KEYWORD);
    while (true) {
      builder.advanceLexer();
      parseExpression(builder, true, false);
      checkMatches(PyTokenTypes.IN_KEYWORD, "'in' expected");
      if (!parseTupleExpression(builder, false, false, true)) {
        builder.error("expression expected");
      }
      while (builder.getTokenType() == PyTokenTypes.IF_KEYWORD) {
        builder.advanceLexer();
        parseExpression();
      }
      if (builder.getTokenType() == endToken) {
        builder.advanceLexer();
        break;
      }
      if (builder.getTokenType() == PyTokenTypes.FOR_KEYWORD) {
        expr.done(exprType);
        expr = expr.precede();
        continue;
      }
      builder.error("closing bracket or 'for' expected");
      break;
    }
    expr.done(exprType);
  }

  private void parseDictLiteralExpression(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.LBRACE);
    final PsiBuilder.Marker expr = builder.mark();
    builder.advanceLexer();
    while (builder.getTokenType() != PyTokenTypes.RBRACE) {
      if (!parseKeyValueExpression(builder)) {
        break;
      }
      if (builder.getTokenType() != PyTokenTypes.RBRACE) {
        checkMatches(PyTokenTypes.COMMA, "comma expected");
      }
    }
    builder.advanceLexer();
    expr.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
  }

  private boolean parseKeyValueExpression(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    if (!parseSingleExpression(builder, false)) {
      marker.drop();
      return false;
    }
    checkMatches(PyTokenTypes.COLON, ": expected");
    if (!parseSingleExpression(builder, false)) {
      marker.drop();
      return false;
    }
    marker.done(PyElementTypes.KEY_VALUE_EXPRESSION);
    return true;
  }

  private void parseParenthesizedExpression(PsiBuilder builder, boolean isTargetExpression) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.LPAR);
    final PsiBuilder.Marker expr = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() == PyTokenTypes.RPAR) {
      builder.advanceLexer();
      expr.done(PyElementTypes.TUPLE_EXPRESSION);
    }
    else {
      parseYieldOrTupleExpression(builder, isTargetExpression);
      if (builder.getTokenType() == PyTokenTypes.FOR_KEYWORD) {
        parseListCompExpression(builder, expr, PyTokenTypes.RPAR, PyElementTypes.GENERATOR_EXPRESSION);
      }
      else {
        checkMatches(PyTokenTypes.RPAR, ") expected");
        expr.done(PyElementTypes.PARENTHESIZED_EXPRESSION);
      }
    }
  }

  private void parseReprExpression(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.TICK);
    final PsiBuilder.Marker expr = builder.mark();
    builder.advanceLexer();
    parseExpression();
    checkMatches(PyTokenTypes.TICK, "` expected");
    expr.done(PyElementTypes.REPR_EXPRESSION);
  }

  public boolean parseMemberExpression(PsiBuilder builder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parsePrimaryExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }

    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == PyTokenTypes.DOT) {
        builder.advanceLexer();
        checkMatches(PyTokenTypes.IDENTIFIER, "name expected");
        if (isTargetExpression && builder.getTokenType() != PyTokenTypes.DOT) {
          expr.done(PyElementTypes.TARGET_EXPRESSION);
        }
        else {
          expr.done(PyElementTypes.REFERENCE_EXPRESSION);
        }
        expr = expr.precede();
      }
      else if (tokenType == PyTokenTypes.LPAR) {
        parseArgumentList(builder);
        expr.done(PyElementTypes.CALL_EXPRESSION);
        expr = expr.precede();
      }
      else if (tokenType == PyTokenTypes.LBRACKET) {
        builder.advanceLexer();
        if (builder.getTokenType() == PyTokenTypes.COLON) {
          PsiBuilder.Marker sliceMarker = builder.mark();
          sliceMarker.done(PyElementTypes.EMPTY_EXPRESSION);
          parseSliceEnd(builder, expr);
        }
        else {
          parseExpressionOptional(builder);
          if (builder.getTokenType() == PyTokenTypes.COLON) {
            parseSliceEnd(builder, expr);
          }
          else {
            checkMatches(PyTokenTypes.RBRACKET, "] expected");
            expr.done(PyElementTypes.SUBSCRIPTION_EXPRESSION);
          }
        }
        expr = expr.precede();
      }
      else {
        expr.drop();
        break;
      }
    }

    return true;
  }

  private void parseSliceEnd(PsiBuilder builder, PsiBuilder.Marker expr) {
    builder.advanceLexer();
    if (builder.getTokenType() == PyTokenTypes.RBRACKET) {
      PsiBuilder.Marker sliceMarker = builder.mark();
      sliceMarker.done(PyElementTypes.EMPTY_EXPRESSION);
      builder.advanceLexer();
    }
    else {
      if (builder.getTokenType() == PyTokenTypes.COLON) {
        PsiBuilder.Marker sliceMarker = builder.mark();
        sliceMarker.done(PyElementTypes.EMPTY_EXPRESSION);
      }
      else {
        parseExpression();
      }
      if (builder.getTokenType() != PyTokenTypes.RBRACKET && builder.getTokenType() != PyTokenTypes.COLON) {
        builder.error(": or ] expected");
      }
      if (builder.getTokenType() == PyTokenTypes.COLON) {
        builder.advanceLexer();
        parseExpressionOptional(builder);
      }
      checkMatches(PyTokenTypes.RBRACKET, "] expected");
    }
    expr.done(PyElementTypes.SLICE_EXPRESSION);
  }

  public void parseArgumentList(final PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.LPAR);
    final PsiBuilder.Marker arglist = builder.mark();
    final PsiBuilder.Marker genexpr = builder.mark();
    builder.advanceLexer();
    int argNumber = 0;
    boolean needBracket = true;
    while (builder.getTokenType() != PyTokenTypes.RPAR) {
      argNumber++;
      if (argNumber > 1) {
        if (argNumber == 2 && builder.getTokenType() == PyTokenTypes.FOR_KEYWORD) {
          parseListCompExpression(builder, genexpr, PyTokenTypes.RPAR, PyElementTypes.GENERATOR_EXPRESSION);
          needBracket = false;
          break;
        }
        else if (builder.getTokenType() == PyTokenTypes.COMMA) {
          builder.advanceLexer();
          if (builder.getTokenType() == PyTokenTypes.RPAR) {
            break;
          }
        }
        else {
          builder.error(", or ) expected");
          break;
        }
      }
      if (builder.getTokenType() == PyTokenTypes.MULT || builder.getTokenType() == PyTokenTypes.EXP) {
        final PsiBuilder.Marker starArgMarker = builder.mark();
        builder.advanceLexer();
        if (!parseSingleExpression(builder, false)) {
          builder.error("expression expected");
        }
        starArgMarker.done(PyElementTypes.STAR_ARGUMENT_EXPRESSION);
      }
      else {
        if (builder.getTokenType() == PyTokenTypes.IDENTIFIER) {
          final PsiBuilder.Marker keywordArgMarker = builder.mark();
          builder.advanceLexer();
          if (builder.getTokenType() == PyTokenTypes.EQ) {
            builder.advanceLexer();
            if (!parseSingleExpression(builder, false)) {
              builder.error("expression expected");
            }
            keywordArgMarker.done(PyElementTypes.KEYWORD_ARGUMENT_EXPRESSION);
            continue;
          }
          keywordArgMarker.rollbackTo();
        }
        if (!parseSingleExpression(builder, false)) {
          builder.error("expression expected");
        }
      }
    }

    if (needBracket) {
      genexpr.drop();
      checkMatches(PyTokenTypes.RPAR, ") expected");
    }
    arglist.done(PyElementTypes.ARGUMENT_LIST);
  }

  public boolean parseExpressionOptional(PsiBuilder builder) {
    return parseTupleExpression(builder, false, false, false);
  }

  public boolean parseExpressionOptional(PsiBuilder builder, boolean isTargetExpression) {
    return parseTupleExpression(builder, false, isTargetExpression, false);
  }

  public void parseExpression() {
    if (!parseExpressionOptional(myBuilder)) {
      myBuilder.error("expression expected");
    }
  }

  public void parseExpression(PsiBuilder builder, boolean stopOnIn, boolean isTargetExpression) {
    if (!parseTupleExpression(builder, stopOnIn, isTargetExpression, false)) {
      builder.error("expression expected");
    }
  }

  public boolean parseYieldOrTupleExpression(final PsiBuilder builder, final boolean isTargetExpression) {
    if (builder.getTokenType() == PyTokenTypes.YIELD_KEYWORD) {
      PsiBuilder.Marker yieldExpr = builder.mark();
      builder.advanceLexer();
      parseTupleExpression(builder, false, isTargetExpression, false);
      yieldExpr.done(PyElementTypes.YIELD_EXPRESSION);
      return true;
    }
    else {
      return parseTupleExpression(builder, false, isTargetExpression, false);
    }
  }

  private boolean parseTupleExpression(final PsiBuilder builder, boolean stopOnIn, boolean isTargetExpression, final boolean oldTest) {
    PsiBuilder.Marker expr = builder.mark();
    boolean exprParseResult = oldTest ? parseOldTestExpression(builder) : parseTestExpression(builder, stopOnIn, isTargetExpression);
    if (!exprParseResult) {
      expr.drop();
      return false;
    }
    if (builder.getTokenType() == PyTokenTypes.COMMA) {
      while (builder.getTokenType() == PyTokenTypes.COMMA) {
        builder.advanceLexer();
        PsiBuilder.Marker expr2 = builder.mark();
        exprParseResult = oldTest ? parseOldTestExpression(builder) : parseTestExpression(builder, stopOnIn, isTargetExpression);
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

  public boolean parseSingleExpression(final PsiBuilder builder, boolean isTargetExpression) {
    return parseTestExpression(builder, false, isTargetExpression);
  }

  private boolean parseTestExpression(final PsiBuilder builder, boolean stopOnIn, boolean isTargetExpression) {
    if (builder.getTokenType() == PyTokenTypes.LAMBDA_KEYWORD) {
      return parseLambdaExpression(builder, false);
    }
    PsiBuilder.Marker condExpr = builder.mark();
    if (!parseORTestExpression(builder, stopOnIn, isTargetExpression)) {
      condExpr.drop();
      return false;
    }
    if (builder.getTokenType() == PyTokenTypes.IF_KEYWORD) {
      builder.advanceLexer();
      if (!parseORTestExpression(builder, stopOnIn, isTargetExpression)) {
        builder.error("expression expected");
      }
      else {
        if (builder.getTokenType() != PyTokenTypes.ELSE_KEYWORD) {
          builder.error("'else' expected");
        }
        else {
          builder.advanceLexer();
          if (!parseTestExpression(builder, stopOnIn, isTargetExpression)) {
            builder.error("expression expected");
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

  private boolean parseOldTestExpression(PsiBuilder builder) {
    if (builder.getTokenType() == PyTokenTypes.LAMBDA_KEYWORD) {
      return parseLambdaExpression(builder, true);
    }
    return parseORTestExpression(builder, false, false);
  }

  private boolean parseLambdaExpression(final PsiBuilder builder, final boolean oldTest) {
    PsiBuilder.Marker expr = builder.mark();
    builder.advanceLexer();
    getFunctionParser().parseParameterListContents(builder, PyTokenTypes.COLON, false);
    boolean parseExpressionResult = oldTest ? parseOldTestExpression(builder) : parseSingleExpression(builder, false);
    if (!parseExpressionResult) {
      builder.error("expression expected");
    }
    expr.done(PyElementTypes.LAMBDA_EXPRESSION);
    return true;
  }

  private boolean parseORTestExpression(final PsiBuilder builder, boolean stopOnIn, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseANDTestExpression(builder, stopOnIn, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (builder.getTokenType() == PyTokenTypes.OR_KEYWORD) {
      builder.advanceLexer();
      if (!parseANDTestExpression(builder, stopOnIn, isTargetExpression)) {
        builder.error("expression expected");
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseANDTestExpression(final PsiBuilder builder, boolean stopOnIn, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseNOTTestExpression(builder, stopOnIn, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (builder.getTokenType() == PyTokenTypes.AND_KEYWORD) {
      builder.advanceLexer();
      if (!parseNOTTestExpression(builder, stopOnIn, isTargetExpression)) {
        builder.error("expression expected");
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseNOTTestExpression(final PsiBuilder builder, boolean stopOnIn, boolean isTargetExpression) {
    if (builder.getTokenType() == PyTokenTypes.NOT_KEYWORD) {
      final PsiBuilder.Marker expr = builder.mark();
      builder.advanceLexer();
      if (!parseNOTTestExpression(builder, stopOnIn, isTargetExpression)) {
        builder.error("Expression expected");
      }
      expr.done(PyElementTypes.PREFIX_EXPRESSION);
      return true;
    }
    else {
      return parseComparisonExpression(builder, stopOnIn, isTargetExpression);
    }
  }

  private boolean parseComparisonExpression(final PsiBuilder builder, boolean stopOnIn, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseBitwiseORExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }
    if (stopOnIn && builder.getTokenType() == PyTokenTypes.IN_KEYWORD) {
      expr.drop();
      return true;
    }
    while (PyTokenTypes.COMPARISON_OPERATIONS.contains(builder.getTokenType())) {
      if (builder.getTokenType() == PyTokenTypes.NOT_KEYWORD) {
        PsiBuilder.Marker notMarker = builder.mark();
        builder.advanceLexer();
        if (builder.getTokenType() != PyTokenTypes.IN_KEYWORD) {
          notMarker.rollbackTo();
          break;
        }
        notMarker.drop();
        builder.advanceLexer();
      }
      else if (builder.getTokenType() == PyTokenTypes.IS_KEYWORD) {
        builder.advanceLexer();
        if (builder.getTokenType() == PyTokenTypes.NOT_KEYWORD) {
          builder.advanceLexer();
        }
      }
      else {
        builder.advanceLexer();
      }

      if (!parseBitwiseORExpression(builder, isTargetExpression)) {
        builder.error("expression expected");
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseBitwiseORExpression(final PsiBuilder builder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseBitwiseXORExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (builder.getTokenType() == PyTokenTypes.OR) {
      builder.advanceLexer();
      if (!parseBitwiseXORExpression(builder, isTargetExpression)) {
        builder.error("expression expected");
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseBitwiseXORExpression(final PsiBuilder builder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseBitwiseANDExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (builder.getTokenType() == PyTokenTypes.XOR) {
      builder.advanceLexer();
      if (!parseBitwiseANDExpression(builder, isTargetExpression)) {
        builder.error("expression expected");
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseBitwiseANDExpression(final PsiBuilder builder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseShiftExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (builder.getTokenType() == PyTokenTypes.AND) {
      builder.advanceLexer();
      if (!parseShiftExpression(builder, isTargetExpression)) {
        builder.error("expression expected");
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseShiftExpression(final PsiBuilder builder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseAdditiveExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (PyTokenTypes.SHIFT_OPERATIONS.contains(builder.getTokenType())) {
      builder.advanceLexer();
      if (!parseAdditiveExpression(builder, isTargetExpression)) {
        builder.error("expression expected");
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseAdditiveExpression(final PsiBuilder builder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseMultiplicativeExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (PyTokenTypes.ADDITIVE_OPERATIONS.contains(builder.getTokenType())) {
      builder.advanceLexer();
      if (!parseMultiplicativeExpression(builder, isTargetExpression)) {
        builder.error("expression expected");
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseMultiplicativeExpression(final PsiBuilder builder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseUnaryExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }

    while (PyTokenTypes.MULTIPLICATIVE_OPERATIONS.contains(builder.getTokenType())) {
      builder.advanceLexer();
      if (!parseUnaryExpression(builder, isTargetExpression)) {
        builder.error("expression expected");
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseUnaryExpression(final PsiBuilder builder, boolean isTargetExpression) {
    final IElementType tokenType = builder.getTokenType();
    if (PyTokenTypes.UNARY_OPERATIONS.contains(tokenType)) {
      final PsiBuilder.Marker expr = builder.mark();
      builder.advanceLexer();
      if (!parseUnaryExpression(builder, isTargetExpression)) {
        builder.error("Expression expected");
      }
      expr.done(PyElementTypes.PREFIX_EXPRESSION);
      return true;
    }
    else {
      return parsePowerExpression(builder, isTargetExpression);
    }
  }

  private boolean parsePowerExpression(final PsiBuilder builder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseMemberExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }

    if (builder.getTokenType() == PyTokenTypes.EXP) {
      builder.advanceLexer();
      if (!parseUnaryExpression(builder, isTargetExpression)) {
        builder.error("expression expected");
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
