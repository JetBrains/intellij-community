// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing;

import com.intellij.lang.SyntaxTreeBuilder;
import com.intellij.lang.WhitespacesAndCommentsBinder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts.ParsingError;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.PyParsingBundle.message;


public class ExpressionParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance(ExpressionParsing.class);
  public static final WhitespacesAndCommentsBinder CONSUME_COMMENTS_AND_SPACES_TO_LEFT = (tokens, atStreamEdge, getter) -> tokens.size();

  public ExpressionParsing(ParsingContext context) {
    super(context);
  }

  public boolean parsePrimaryExpression(boolean isTargetExpression) {
    final IElementType firstToken = myBuilder.getTokenType();
    if (isIdentifier(myBuilder)) {
      if (isTargetExpression) {
        buildTokenElement(PyElementTypes.TARGET_EXPRESSION, myBuilder);
      }
      else {
        buildTokenElement(getReferenceType(), myBuilder);
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
    else if (firstToken == PyTokenTypes.TRUE_KEYWORD ||
             firstToken == PyTokenTypes.FALSE_KEYWORD ||
             firstToken == PyTokenTypes.DEBUG_KEYWORD) {
      buildTokenElement(PyElementTypes.BOOL_LITERAL_EXPRESSION, myBuilder);
      return true;
    }
    else if (PyTokenTypes.STRING_NODES.contains(firstToken) || firstToken == PyTokenTypes.FSTRING_START) {
      return parseStringLiteralExpression();
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
    else if (parseEllipsis()) {
      return true;
    }
    return false;
  }

  public boolean parseStringLiteralExpression() {
    final SyntaxTreeBuilder builder = myContext.getBuilder();
    IElementType tokenType = builder.getTokenType();
    if (PyTokenTypes.STRING_NODES.contains(tokenType) || tokenType == PyTokenTypes.FSTRING_START) {
      final SyntaxTreeBuilder.Marker marker = builder.mark();
      while (true) {
        tokenType = builder.getTokenType();
        if (PyTokenTypes.STRING_NODES.contains(tokenType)) {
          nextToken();
        }
        else if (tokenType == PyTokenTypes.FSTRING_START) {
          parseFormattedStringNode();
        }
        else {
          break;
        }
      }
      marker.done(PyElementTypes.STRING_LITERAL_EXPRESSION);
      return true;
    }
    return false;
  }

  private void parseFormattedStringNode() {
    final SyntaxTreeBuilder builder = myContext.getBuilder();
    if (atToken(PyTokenTypes.FSTRING_START)) {
      final String prefixThenQuotes = builder.getTokenText();
      assert prefixThenQuotes != null;
      final String openingQuotes = prefixThenQuotes.replaceFirst("^[UuBbCcRrFfTt]*", "");
      final SyntaxTreeBuilder.Marker marker = builder.mark();
      nextToken();
      while (true) {
        if (atAnyOfTokens(PyTokenTypes.FSTRING_TEXT_TOKENS)) {
          nextToken();
        }
        else if (atToken(PyTokenTypes.FSTRING_FRAGMENT_START)) {
          parseFStringFragment();
        }
        else if (atToken(PyTokenTypes.FSTRING_END)) {
          if (builder.getTokenText().equals(openingQuotes)) {
            nextToken();
          }
          // Can be the end of an enclosing f-string, so leave it in the stream
          else {
            builder.mark().error(message("PARSE.expected.fstring.quote", openingQuotes));
          }
          break;
        }
        else if (atToken(PyTokenTypes.STATEMENT_BREAK)) {
          builder.mark().error(message("PARSE.expected.fstring.quote", openingQuotes));
          break;
        }
        else {
          builder.error(message("unexpected.f.string.token"));
          break;
        }
      }
      marker.done(PyElementTypes.FSTRING_NODE);
    }
  }

  private void parseFStringFragment() {
    final SyntaxTreeBuilder builder = myContext.getBuilder();
    if (atToken(PyTokenTypes.FSTRING_FRAGMENT_START)) {
      final SyntaxTreeBuilder.Marker marker = builder.mark();
      nextToken();
      SyntaxTreeBuilder.Marker recoveryMarker = builder.mark();
      final boolean parsedExpression = myContext.getExpressionParser().parseExpressionOptional();
      if (parsedExpression) {
        recoveryMarker.drop();
        recoveryMarker = builder.mark();
      }
      boolean recovery = !parsedExpression;
      while (!builder.eof() && !atAnyOfTokens(PyTokenTypes.FSTRING_FRAGMENT_TYPE_CONVERSION,
                                              PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START,
                                              PyTokenTypes.FSTRING_FRAGMENT_END,
                                              PyTokenTypes.FSTRING_END,
                                              PyTokenTypes.STATEMENT_BREAK,
                                              PyTokenTypes.EQ)) {
        nextToken();
        recovery = true;
      }
      if (recovery) {
        recoveryMarker.error(parsedExpression ? message("unexpected.expression.part") : message("PARSE.expected.expression"));
        recoveryMarker.setCustomEdgeTokenBinders(null, CONSUME_COMMENTS_AND_SPACES_TO_LEFT);
      }
      else {
        recoveryMarker.drop();
      }

      matchToken(PyTokenTypes.EQ);
      final boolean hasTypeConversion = matchToken(PyTokenTypes.FSTRING_FRAGMENT_TYPE_CONVERSION);
      final boolean hasFormatPart = atToken(PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START);
      if (hasFormatPart) {
        parseFStringFragmentFormatPart();
      }
      @ParsingError String errorMessage = message("PARSE.expected.fstring.rbrace");
      if (!hasFormatPart && !atToken(PyTokenTypes.FSTRING_END)) {
        errorMessage = message("PARSE.expected.fstring.colon.or.rbrace");
        if (!hasTypeConversion) {
          errorMessage = message("PARSE.expected.fstring.type.conversion.or.colon.or.rbrace");
        }
      }

      checkMatches(PyTokenTypes.FSTRING_FRAGMENT_END, errorMessage);
      marker.setCustomEdgeTokenBinders(null, CONSUME_COMMENTS_AND_SPACES_TO_LEFT);
      marker.done(PyElementTypes.FSTRING_FRAGMENT);
    }
  }

  private void parseFStringFragmentFormatPart() {
    if (atToken(PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START)) {
      final SyntaxTreeBuilder.Marker marker = myContext.getBuilder().mark();
      nextToken();
      while (true) {
        if (atAnyOfTokens(PyTokenTypes.FSTRING_TEXT_TOKENS)) {
          nextToken();
        }
        else if (atToken(PyTokenTypes.FSTRING_FRAGMENT_START)) {
          parseFStringFragment();
        }
        else {
          break;
        }
      }
      marker.done(PyElementTypes.FSTRING_FRAGMENT_FORMAT_PART);
    }
  }

  private void parseListLiteralExpression(final SyntaxTreeBuilder builder, boolean isTargetExpression) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.LBRACKET);
    final SyntaxTreeBuilder.Marker expr = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() == PyTokenTypes.RBRACKET) {
      builder.advanceLexer();
      expr.done(PyElementTypes.LIST_LITERAL_EXPRESSION);
      return;
    }
    if (!parseNamedTestExpression(false, isTargetExpression)) {
      builder.error(message("PARSE.expected.expression"));
    }
    if (atForOrAsyncFor()) {
      parseComprehension(expr, PyTokenTypes.RBRACKET, PyElementTypes.LIST_COMP_EXPRESSION);
    }
    else {
      while (builder.getTokenType() != PyTokenTypes.RBRACKET) {
        if (!matchToken(PyTokenTypes.COMMA)) {
          builder.error(message("rbracket.or.comma.expected"));
        }
        if (atToken(PyTokenTypes.RBRACKET)) {
          break;
        }
        if (!parseNamedTestExpression(false, isTargetExpression)) {
          builder.error(message("PARSE.expected.expr.or.comma.or.bracket"));
          break;
        }
      }
      checkMatches(PyTokenTypes.RBRACKET, message("PARSE.expected.rbracket"));
      expr.done(PyElementTypes.LIST_LITERAL_EXPRESSION);
    }
  }

  private void parseComprehension(SyntaxTreeBuilder.Marker expr,
                                  final @Nullable IElementType endToken,
                                  final IElementType exprType) {
    assertCurrentToken(PyTokenTypes.FOR_KEYWORD);
    while (true) {
      myBuilder.advanceLexer();
      parseStarTargets();
      parseComprehensionRange(exprType == PyElementTypes.GENERATOR_EXPRESSION);
      while (myBuilder.getTokenType() == PyTokenTypes.IF_KEYWORD) {
        myBuilder.advanceLexer();
        if (!parseOldExpression()) {
          myBuilder.error(message("PARSE.expected.expression"));
        }
      }
      if (atForOrAsyncFor()) {
        continue;
      }
      if (endToken == null || matchToken(endToken)) {
        break;
      }
      myBuilder.error(message("PARSE.expected.for.or.bracket"));
      break;
    }
    expr.done(exprType);
  }

  public boolean parseStarTargets() {
    SyntaxTreeBuilder.Marker expr = myBuilder.mark();
    if (!parseStarExpression(true)) {
      myBuilder.error(message("PARSE.expected.expression"));
      expr.drop();
      return false;
    }
    if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
      while (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
        myBuilder.advanceLexer();
        SyntaxTreeBuilder.Marker expr2 = myBuilder.mark();
        if (!parseStarExpression(true)) {
          myBuilder.error(message("PARSE.expected.expression"));
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

  protected void parseComprehensionRange(boolean generatorExpression) {
    checkMatches(PyTokenTypes.IN_KEYWORD, message("PARSE.expected.in"));
    boolean result;
    if (generatorExpression) {
      result = parseORTestExpression(false, false);
    }
    else {
      result = parseTupleExpression(false, false, true);
    }
    if (!result) {
      myBuilder.error(message("PARSE.expected.expression"));
    }
  }

  private void parseDictOrSetDisplay() {
    LOG.assertTrue(myBuilder.getTokenType() == PyTokenTypes.LBRACE);
    final SyntaxTreeBuilder.Marker expr = myBuilder.mark();
    myBuilder.advanceLexer();

    if (matchToken(PyTokenTypes.RBRACE)) {
      expr.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
      return;
    }

    if (atToken(PyTokenTypes.EXP)) {
      if (!parseDoubleStarExpression(false)) {
        myBuilder.error(message("PARSE.expected.expression"));
        expr.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
        return;
      }
      parseDictLiteralContentTail(expr);
      return;
    }

    parseDictOrSetTail(expr, false);
  }

  private void parseDictOrSetTail(@NotNull SyntaxTreeBuilder.Marker expr, boolean isDict) {
    final SyntaxTreeBuilder.Marker firstExprMarker = myBuilder.mark();

    if (isDict && !parseSingleExpression(false) || !isDict && !parseNamedTestExpression(false, false)) {
      myBuilder.error(message("PARSE.expected.expression"));
      firstExprMarker.drop();
      expr.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
      return;
    }

    if (matchToken(PyTokenTypes.COLON)) {
      if (!isDict) {
        firstExprMarker.rollbackTo();
        parseDictOrSetTail(expr, true);
        return;
      }
      parseDictLiteralTail(expr, firstExprMarker);
    }
    else if (atToken(PyTokenTypes.COMMA) || atToken(PyTokenTypes.RBRACE)) {
      firstExprMarker.drop();
      parseSetLiteralTail(expr);
    }
    else if (atForOrAsyncFor()) {
      firstExprMarker.drop();
      parseComprehension(expr, PyTokenTypes.RBRACE, PyElementTypes.SET_COMP_EXPRESSION);
    }
    else {
      myBuilder.error(message("PARSE.expected.expression"));
      firstExprMarker.drop();
      expr.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
    }
  }

  private void parseDictLiteralTail(SyntaxTreeBuilder.Marker startMarker, SyntaxTreeBuilder.Marker firstKeyValueMarker) {
    if (!parseSingleExpression(false)) {
      myBuilder.error(message("PARSE.expected.expression"));
      firstKeyValueMarker.done(PyElementTypes.KEY_VALUE_EXPRESSION);
      if (atToken(PyTokenTypes.RBRACE)) {
        myBuilder.advanceLexer();
      }
      startMarker.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
      return;
    }
    firstKeyValueMarker.done(PyElementTypes.KEY_VALUE_EXPRESSION);
    if (atForOrAsyncFor()) {
      parseComprehension(startMarker, PyTokenTypes.RBRACE, PyElementTypes.DICT_COMP_EXPRESSION);
    }
    else {
      parseDictLiteralContentTail(startMarker);
    }
  }

  private void parseDictLiteralContentTail(SyntaxTreeBuilder.Marker startMarker) {
    while (myBuilder.getTokenType() != PyTokenTypes.RBRACE) {
      checkMatches(PyTokenTypes.COMMA, message("PARSE.expected.comma"));
      if (atToken(PyTokenTypes.EXP)) {
        if (!parseDoubleStarExpression(false)) {
          break;
        }
      }
      else {
        if (!parseKeyValueExpression()) {
          break;
        }
      }
    }
    checkMatches(PyTokenTypes.RBRACE, message("PARSE.expected.rbrace"));
    startMarker.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
  }

  private boolean parseKeyValueExpression() {
    final SyntaxTreeBuilder.Marker marker = myBuilder.mark();
    if (!parseSingleExpression(false)) {
      marker.drop();
      return false;
    }
    checkMatches(PyTokenTypes.COLON, message("PARSE.expected.colon"));
    if (!parseSingleExpression(false)) {
      myBuilder.error(message("value.expression.expected"));
      marker.drop();
      return false;
    }
    marker.done(PyElementTypes.KEY_VALUE_EXPRESSION);
    return true;
  }

  private void parseSetLiteralTail(SyntaxTreeBuilder.Marker startMarker) {
    while (myBuilder.getTokenType() != PyTokenTypes.RBRACE) {
      checkMatches(PyTokenTypes.COMMA, message("PARSE.expected.comma"));
      if (!parseNamedTestExpression(false, false)) {
        break;
      }
    }
    checkMatches(PyTokenTypes.RBRACE, message("PARSE.expected.rbrace"));
    startMarker.done(PyElementTypes.SET_LITERAL_EXPRESSION);
  }

  private void parseParenthesizedExpression(boolean isTargetExpression) {
    LOG.assertTrue(myBuilder.getTokenType() == PyTokenTypes.LPAR);
    final SyntaxTreeBuilder.Marker expr = myBuilder.mark();
    myBuilder.advanceLexer();
    if (myBuilder.getTokenType() == PyTokenTypes.RPAR) {
      myBuilder.advanceLexer();
      expr.done(PyElementTypes.TUPLE_EXPRESSION);
    }
    else {
      parseYieldOrTupleExpression(isTargetExpression);
      if (atForOrAsyncFor()) {
        parseComprehension(expr, PyTokenTypes.RPAR, PyElementTypes.GENERATOR_EXPRESSION);
      }
      else {
        final SyntaxTreeBuilder.Marker err = myBuilder.mark();
        boolean empty = true;
        while (!myBuilder.eof() &&
               myBuilder.getTokenType() != PyTokenTypes.RPAR &&
               myBuilder.getTokenType() != PyTokenTypes.LINE_BREAK &&
               myBuilder.getTokenType() != PyTokenTypes.STATEMENT_BREAK &&
               myBuilder.getTokenType() != PyTokenTypes.FSTRING_END) {
          myBuilder.advanceLexer();
          empty = false;
        }
        if (!empty) {
          err.error(message("unexpected.expression.syntax"));
        }
        else {
          err.drop();
        }
        checkMatches(PyTokenTypes.RPAR, message("PARSE.expected.rpar"));
        expr.done(PyElementTypes.PARENTHESIZED_EXPRESSION);
      }
    }
  }

  private void parseReprExpression(SyntaxTreeBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.TICK);
    final SyntaxTreeBuilder.Marker expr = builder.mark();
    builder.advanceLexer();
    parseExpression();
    checkMatches(PyTokenTypes.TICK, message("PARSE.expected.tick"));
    expr.done(PyElementTypes.REPR_EXPRESSION);
  }

  public boolean parseMemberExpression(boolean isTargetExpression) {
    // in sequence a.b.... .c all members but last are always references, and the last may be target.
    boolean recastFirstIdentifier = false;
    boolean recastQualifier = false;
    do {
      boolean firstIdentifierIsTarget = isTargetExpression && !recastFirstIdentifier;
      SyntaxTreeBuilder.Marker expr = myBuilder.mark();
      if (!parsePrimaryExpression(firstIdentifierIsTarget)) {
        expr.drop();
        return false;
      }

      while (true) {
        final IElementType tokenType = myBuilder.getTokenType();
        if (tokenType == PyTokenTypes.DOT) {
          if (firstIdentifierIsTarget) {
            recastFirstIdentifier = true;
            expr.rollbackTo();
            break;
          }
          myBuilder.advanceLexer();
          checkMatches(PyTokenTypes.IDENTIFIER, message("PARSE.expected.name"));
          if (isTargetExpression && !recastQualifier && !atAnyOfTokens(PyTokenTypes.DOT, PyTokenTypes.LPAR, PyTokenTypes.LBRACKET)) {
            expr.done(PyElementTypes.TARGET_EXPRESSION);
          }
          else {
            expr.done(getReferenceType());
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
          parseSliceOrSubscriptionExpression(expr, false);
          if (isTargetExpression && !recastQualifier) {
            recastFirstIdentifier = true; // subscription is always a reference
            recastQualifier = true; // recast non-first qualifiers too
            expr.rollbackTo();
            break;
          }
          expr = expr.precede();
        }
        else {
          expr.drop();
          break;
        }
        recastFirstIdentifier = false; // it is true only after a break; normal flow always unsets it.
        // recastQualifier is untouched, it remembers whether qualifiers were already recast
      }
    }
    while (recastFirstIdentifier);

    return true;
  }

  private void parseSliceOrSubscriptionExpression(@NotNull SyntaxTreeBuilder.Marker expr, boolean isSlice) {
    SyntaxTreeBuilder.Marker sliceOrTupleStart = myBuilder.mark();
    SyntaxTreeBuilder.Marker sliceItemStart = myBuilder.mark();
    if (atToken(PyTokenTypes.COLON)) {
      sliceOrTupleStart.drop();
      SyntaxTreeBuilder.Marker sliceMarker = myBuilder.mark();
      sliceMarker.done(PyElementTypes.EMPTY_EXPRESSION);
      parseSliceEnd(expr, sliceItemStart);
    }
    else {
      var hadExpression = isSlice ? parseSingleExpression(false) : parseNamedTestExpression(false, false);
      if (atToken(PyTokenTypes.COLON)) {
        if (!isSlice) {
          sliceOrTupleStart.rollbackTo();
          parseSliceOrSubscriptionExpression(expr, true);
          return;
        }
        sliceOrTupleStart.drop();
        parseSliceEnd(expr, sliceItemStart);
      }
      else if (atToken(PyTokenTypes.COMMA)) {
        sliceItemStart.done(PyElementTypes.SLICE_ITEM);
        if (!parseSliceListTail(expr, sliceOrTupleStart)) {
          sliceOrTupleStart.rollbackTo();
          if (!parseTupleExpression(false, false, false)) {
            myBuilder.error(message("tuple.expression.expected"));
          }
          checkMatches(PyTokenTypes.RBRACKET, message("PARSE.expected.rbracket"));
          expr.done(PyElementTypes.SUBSCRIPTION_EXPRESSION);
        }
      }
      else {
        if (!hadExpression) {
          myBuilder.error(message("PARSE.expected.expression"));
        }
        sliceOrTupleStart.drop();
        sliceItemStart.drop();
        checkMatches(PyTokenTypes.RBRACKET, message("PARSE.expected.rbracket"));
        expr.done(PyElementTypes.SUBSCRIPTION_EXPRESSION);
      }
    }
  }

  private boolean parseEllipsis() {
    if (atToken(PyTokenTypes.DOT)) {
      final SyntaxTreeBuilder.Marker maybeEllipsis = myBuilder.mark();
      myBuilder.advanceLexer();
      //duplication is intended as matchToken advances the lexer
      //noinspection DuplicateBooleanBranch
      if (matchToken(PyTokenTypes.DOT) && matchToken(PyTokenTypes.DOT)) {
        maybeEllipsis.done(PyElementTypes.NONE_LITERAL_EXPRESSION);
        return true;
      }
      maybeEllipsis.rollbackTo();
    }
    return false;
  }

  private static final TokenSet BRACKET_OR_COMMA = TokenSet.create(PyTokenTypes.RBRACKET, PyTokenTypes.COMMA);
  private static final TokenSet BRACKET_COLON_COMMA = TokenSet.create(PyTokenTypes.RBRACKET, PyTokenTypes.COLON, PyTokenTypes.COMMA);

  public void parseSliceEnd(SyntaxTreeBuilder.Marker exprStart, SyntaxTreeBuilder.Marker sliceItemStart) {
    myBuilder.advanceLexer();
    if (atToken(PyTokenTypes.RBRACKET)) {
      SyntaxTreeBuilder.Marker sliceMarker = myBuilder.mark();
      sliceMarker.done(PyElementTypes.EMPTY_EXPRESSION);
      sliceItemStart.done(PyElementTypes.SLICE_ITEM);
      nextToken();
      exprStart.done(PyElementTypes.SUBSCRIPTION_EXPRESSION);
      return;
    }
    else {
      if (atToken(PyTokenTypes.COLON)) {
        SyntaxTreeBuilder.Marker sliceMarker = myBuilder.mark();
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
        myBuilder.error(message("rbracket.or.comma.expected"));
      }
    }

    parseSliceListTail(exprStart, null);
  }

  private boolean parseSliceListTail(SyntaxTreeBuilder.Marker exprStart, @Nullable SyntaxTreeBuilder.Marker sliceOrTupleStart) {
    boolean inSlice = sliceOrTupleStart == null;
    while (atToken(PyTokenTypes.COMMA)) {
      nextToken();
      SyntaxTreeBuilder.Marker sliceItemStart = myBuilder.mark();
      parseTestExpression(false, false);
      if (matchToken(PyTokenTypes.COLON)) {
        inSlice = true;
        parseTestExpression(false, false);
        if (matchToken(PyTokenTypes.COLON)) {
          parseTestExpression(false, false);
        }
      }
      sliceItemStart.done(PyElementTypes.SLICE_ITEM);
      if (!BRACKET_OR_COMMA.contains(myBuilder.getTokenType())) {
        myBuilder.error(message("rbracket.or.comma.expected"));
        break;
      }
    }
    checkMatches(PyTokenTypes.RBRACKET, message("PARSE.expected.rbracket"));

    if (inSlice) {
      if (sliceOrTupleStart != null) {
        sliceOrTupleStart.drop();
      }
      exprStart.done(PyElementTypes.SUBSCRIPTION_EXPRESSION);
    }
    return inSlice;
  }

  public void parseArgumentList() {
    LOG.assertTrue(myBuilder.getTokenType() == PyTokenTypes.LPAR);
    final SyntaxTreeBuilder.Marker arglist = myBuilder.mark();
    myBuilder.advanceLexer();
    SyntaxTreeBuilder.Marker genexpr = myBuilder.mark();
    int argNumber = 0;
    while (myBuilder.getTokenType() != PyTokenTypes.RPAR) {
      argNumber++;
      if (argNumber > 1) {
        if (argNumber == 2 && atForOrAsyncFor() && genexpr != null) {
          parseComprehension(genexpr, null, PyElementTypes.GENERATOR_EXPRESSION);
          genexpr = null;
          continue;
        }
        else if (matchToken(PyTokenTypes.COMMA)) {
          if (atToken(PyTokenTypes.RPAR)) {
            break;
          }
        }
        else {
          myBuilder.error(message("PARSE.expected.comma.or.rpar"));
          break;
        }
      }
      if (myBuilder.getTokenType() == PyTokenTypes.MULT || myBuilder.getTokenType() == PyTokenTypes.EXP) {
        final SyntaxTreeBuilder.Marker starArgMarker = myBuilder.mark();
        myBuilder.advanceLexer();
        if (!parseSingleExpression(false)) {
          myBuilder.error(message("PARSE.expected.expression"));
        }
        starArgMarker.done(PyElementTypes.STAR_ARGUMENT_EXPRESSION);
      }
      else {
        if (isIdentifier(myBuilder)) {
          final SyntaxTreeBuilder.Marker keywordArgMarker = myBuilder.mark();
          advanceIdentifierLike(myBuilder);
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
        if (!parseNamedTestExpression(false, false)) {
          myBuilder.error(message("PARSE.expected.expression"));
          break;
        }
      }
    }

    if (genexpr != null) {
      genexpr.drop();
    }
    checkMatches(PyTokenTypes.RPAR, message("PARSE.expected.rpar"));
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
      SyntaxTreeBuilder.Marker yieldExpr = myBuilder.mark();
      myBuilder.advanceLexer();
      if (myBuilder.getTokenType() == PyTokenTypes.FROM_KEYWORD) {
        myBuilder.advanceLexer();
        final boolean parsed = parseTupleExpression(false, isTargetExpression, false);
        if (!parsed) {
          myBuilder.error(message("PARSE.expected.expression"));
        }
        yieldExpr.done(PyElementTypes.YIELD_EXPRESSION);
        return parsed;
      }
      else {
        parseTupleExpression(false, isTargetExpression, false);
        yieldExpr.done(PyElementTypes.YIELD_EXPRESSION);
        return true;
      }
    }
    else {
      return parseTupleExpression(false, isTargetExpression, false);
    }
  }

  protected boolean parseTupleExpression(boolean stopOnIn, boolean isTargetExpression, final boolean oldTest) {
    SyntaxTreeBuilder.Marker expr = myBuilder.mark();
    boolean exprParseResult = oldTest ? parseOldTestExpression() : parseNamedTestExpression(stopOnIn, isTargetExpression);
    if (!exprParseResult) {
      expr.drop();
      return false;
    }
    if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
      while (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
        myBuilder.advanceLexer();
        SyntaxTreeBuilder.Marker expr2 = myBuilder.mark();
        exprParseResult = oldTest ? parseOldTestExpression() : parseNamedTestExpression(stopOnIn, isTargetExpression);
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

  public boolean parseNamedTestExpression(boolean stopOnIn, boolean isTargetExpression) {
    final SyntaxTreeBuilder.Marker expr = myBuilder.mark();

    if (isIdentifier(myBuilder) && myBuilder.lookAhead(1) == PyTokenTypes.COLONEQ) {
      buildTokenElement(PyElementTypes.TARGET_EXPRESSION, myBuilder);

      myBuilder.advanceLexer();

      if (!parseTestExpression(stopOnIn, false)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }

      expr.done(PyElementTypes.ASSIGNMENT_EXPRESSION);
      return true;
    }
    else if (parseTestExpression(stopOnIn, isTargetExpression)) {
      if (!atToken(PyTokenTypes.COLONEQ)) {
        expr.drop();
        return true;
      }
      else {
        // we intentionally allow syntactically illegal assignment expressions like `self.attr := 42` or `xs[0] := 42` for user convenience
        // but don't parse qualified references in LHS as target expressions (unlike in assignment statements)

        myBuilder.error(message("PARSE.expected.identifier"));

        myBuilder.advanceLexer();

        if (!parseTestExpression(stopOnIn, false)) {
          myBuilder.error(message("PARSE.expected.expression"));
        }

        expr.done(PyElementTypes.ASSIGNMENT_EXPRESSION);
        return false;
      }
    }
    else {
      expr.drop();
      return false;
    }
  }

  private boolean parseTestExpression(boolean stopOnIn, boolean isTargetExpression) {
    if (myBuilder.getTokenType() == PyTokenTypes.LAMBDA_KEYWORD) {
      return parseLambdaExpression(false);
    }
    SyntaxTreeBuilder.Marker condExpr = myBuilder.mark();
    if (!parseORTestExpression(stopOnIn, isTargetExpression)) {
      condExpr.drop();
      return false;
    }
    if (myBuilder.getTokenType() == PyTokenTypes.IF_KEYWORD) {
      SyntaxTreeBuilder.Marker conditionMarker = myBuilder.mark();
      myBuilder.advanceLexer();
      if (!parseORTestExpression(stopOnIn, isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      else {
        if (myBuilder.getTokenType() != PyTokenTypes.ELSE_KEYWORD) {
          if (atToken(PyTokenTypes.COLON)) {   // it's regular if statement. Bracket wasn't closed or new line was lost
            conditionMarker.rollbackTo();
            condExpr.drop();
            return true;
          }
          else {
            myBuilder.error(message("PARSE.expected.else"));
          }
        }
        else {
          myBuilder.advanceLexer();
          if (!parseTestExpression(stopOnIn, isTargetExpression)) {
            myBuilder.error(message("PARSE.expected.expression"));
          }
        }
      }
      conditionMarker.drop();
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
    SyntaxTreeBuilder.Marker expr = myBuilder.mark();
    myBuilder.advanceLexer();
    getFunctionParser().parseParameterListContents(PyTokenTypes.COLON, false, true);
    boolean parseExpressionResult = oldTest ? parseOldTestExpression() : parseSingleExpression(false);
    if (!parseExpressionResult) {
      myBuilder.error(message("PARSE.expected.expression"));
    }
    expr.done(PyElementTypes.LAMBDA_EXPRESSION);
    return true;
  }

  protected boolean parseORTestExpression(boolean stopOnIn, boolean isTargetExpression) {
    SyntaxTreeBuilder.Marker expr = myBuilder.mark();
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
    SyntaxTreeBuilder.Marker expr = myBuilder.mark();
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
      final SyntaxTreeBuilder.Marker expr = myBuilder.mark();
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
    SyntaxTreeBuilder.Marker expr = myBuilder.mark();
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
        SyntaxTreeBuilder.Marker notMarker = myBuilder.mark();
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
      SyntaxTreeBuilder.Marker starExpr = myBuilder.mark();
      nextToken();
      if (!parseBitwiseORExpression(isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
        starExpr.drop();
        return false;
      }
      starExpr.done(PyElementTypes.STAR_EXPRESSION);
      return true;
    }
    return parseBitwiseORExpression(isTargetExpression);
  }

  private boolean parseDoubleStarExpression(boolean isTargetExpression) {
    if (atToken(PyTokenTypes.EXP)) {
      SyntaxTreeBuilder.Marker starExpr = myBuilder.mark();
      nextToken();
      if (!parseBitwiseORExpression(isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
        starExpr.drop();
        return false;
      }
      starExpr.done(PyElementTypes.DOUBLE_STAR_EXPRESSION);
      return true;
    }
    return parseBitwiseORExpression(isTargetExpression);
  }

  private boolean parseBitwiseORExpression(boolean isTargetExpression) {
    SyntaxTreeBuilder.Marker expr = myBuilder.mark();
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
    SyntaxTreeBuilder.Marker expr = myBuilder.mark();
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
    SyntaxTreeBuilder.Marker expr = myBuilder.mark();
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
    SyntaxTreeBuilder.Marker expr = myBuilder.mark();
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

  private boolean parseAdditiveExpression(final SyntaxTreeBuilder myBuilder, boolean isTargetExpression) {
    SyntaxTreeBuilder.Marker expr = myBuilder.mark();
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
    SyntaxTreeBuilder.Marker expr = myBuilder.mark();
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

  protected boolean parseUnaryExpression(boolean isTargetExpression) {
    final IElementType tokenType = myBuilder.getTokenType();
    if (PyTokenTypes.UNARY_OPERATIONS.contains(tokenType)) {
      final SyntaxTreeBuilder.Marker expr = myBuilder.mark();
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
    SyntaxTreeBuilder.Marker expr = myBuilder.mark();
    if (!parseAwaitExpression(isTargetExpression)) {
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

  private boolean parseAwaitExpression(boolean isTargetExpression) {
    if (atToken(PyTokenTypes.AWAIT_KEYWORD)) {
      final SyntaxTreeBuilder.Marker expr = myBuilder.mark();
      myBuilder.advanceLexer();

      if (!parseMemberExpression(isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
        expr.done(PyElementTypes.PREFIX_EXPRESSION);
      }
      else {
        expr.done(PyElementTypes.PREFIX_EXPRESSION);
      }

      return true;
    }
    else {
      return parseMemberExpression(isTargetExpression);
    }
  }

  private boolean atForOrAsyncFor() {
    if (atToken(PyTokenTypes.FOR_KEYWORD)) {
      return true;
    }
    else if (matchToken(PyTokenTypes.ASYNC_KEYWORD)) {
      if (atToken(PyTokenTypes.FOR_KEYWORD)) {
        return true;
      }
      else {
        myBuilder.error(message("for.expected"));
        return false;
      }
    }
    return false;
  }
}
