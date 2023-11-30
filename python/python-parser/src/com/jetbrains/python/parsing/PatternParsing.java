// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing;

import com.intellij.lang.SyntaxTreeBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyParsingBundle;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;

public class PatternParsing extends Parsing {
  private final Stack<IElementType> myPendingClosingBraces = new Stack<>();

  protected PatternParsing(@NotNull ParsingContext context) {
    super(context);
  }

  public boolean parseCasePattern() {
    try {
      return parseMaybeSequencePatternWithoutParentheses();
    }
    finally {
      myPendingClosingBraces.clear();
    }
  }

  private boolean parseMaybeSequencePatternWithoutParentheses() {
    SyntaxTreeBuilder.Marker mark = myBuilder.mark();
    CommaSeparation result = parseCommaSeparatedPatterns(this::parsePattern);
    if (result != CommaSeparation.NO_ELEMENTS) {
      if (result == CommaSeparation.EXISTS) {
        mark.done(PyElementTypes.SEQUENCE_PATTERN);
      }
      else {
        mark.drop();
      }
      return true;
    }
    mark.drop();
    return false;
  }

  private boolean parsePattern() {
    return parseMaybeAsPattern();
  }

  private boolean parseMaybeAsPattern() {
    SyntaxTreeBuilder.Marker mark = myBuilder.mark();
    if (parseMaybeOrPattern()) {
      if (atToken(PyTokenTypes.AS_KEYWORD)) {
        nextToken();
        if (atSimpleNameNotUnderscore()) {
          buildTokenElement(PyElementTypes.TARGET_EXPRESSION, myBuilder);
        }
        else {
          myBuilder.error(PyParsingBundle.message("PARSE.expected.name"));
        }
        mark.done(PyElementTypes.AS_PATTERN);
        consumeIllegalLeftoverExpressionTokens();
      }
      else {
        mark.drop();
      }
      return true;
    }
    mark.drop();
    return false;
  }

  private boolean parseMaybeOrPattern() {
    SyntaxTreeBuilder.Marker mark = myBuilder.mark();
    if (parseClosedPattern()) {
      if (atToken(PyTokenTypes.OR)) {
        while (atToken(PyTokenTypes.OR)) {
          nextToken();
          if (!parseClosedPattern()) {
            myBuilder.error(PyParsingBundle.message("PARSE.expected.pattern"));
            break;
          }
        }
        mark.done(PyElementTypes.OR_PATTERN);
      }
      else {
        mark.drop();
      }
      return true;
    }
    mark.drop();
    return false;
  }

  private boolean parseClosedPattern() {
    boolean parsed = (parseLiteralPattern() ||
                      parseGroupOrParenthesizedSequencePattern() ||
                      parseSequencePatternInBrackets() ||
                      parseMappingPattern() ||
                      parseClassPattern() ||
                      parseValuePattern() ||
                      parseWildcardPattern() ||
                      parseCapturePattern() ||
                      // The following two are allowed only inside mapping and sequence patterns respectively,
                      // but from recovery standpoint, it's easier to match them universally and then report
                      // wrong contexts in a dedicated annotator.
                      parseDoubleStarPattern() ||
                      parseSingleStarPattern());
    if (parsed) {
      consumeIllegalLeftoverExpressionTokens();
    }
    return parsed;
  }

  private boolean parseClassPattern() {
    if (atToken(PyTokenTypes.IDENTIFIER)) {
      SyntaxTreeBuilder.Marker mark = myBuilder.mark();
      parseReferenceExpression();
      if (!atToken(PyTokenTypes.LPAR)) {
        mark.rollbackTo();
        return false;
      }
      parseClassPatternArgumentList();
      mark.done(PyElementTypes.CLASS_PATTERN);
      return true;
    }
    return false;
  }

  private void parseClassPatternArgumentList() {
    assert atToken(PyTokenTypes.LPAR);
    SyntaxTreeBuilder.Marker mark = myBuilder.mark();
    nextToken();
    registerConsumedBracket(PyTokenTypes.LPAR);
    parseCommaSeparatedPatterns(this::parseClassPatternArgument);
    if (checkMatches(PyTokenTypes.RPAR, PyParsingBundle.message("PARSE.expected.rpar"))) {
      registerConsumedBracket(PyTokenTypes.RPAR);
    }
    mark.done(PyElementTypes.PATTERN_ARGUMENT_LIST);
  }

  private boolean parseClassPatternArgument() {
    if (atToken(PyTokenTypes.IDENTIFIER) && myBuilder.lookAhead(1) == PyTokenTypes.EQ) {
      SyntaxTreeBuilder.Marker mark = myBuilder.mark();
      nextToken();
      nextToken();
      if (!parsePattern()) {
        myBuilder.error(PyParsingBundle.message("PARSE.expected.pattern"));
      }
      mark.done(PyElementTypes.KEYWORD_PATTERN);
      return true;
    }
    return parsePattern();
  }

  private boolean parseMappingPattern() {
    if (atToken(PyTokenTypes.LBRACE)) {
      SyntaxTreeBuilder.Marker mark = myBuilder.mark();
      nextToken();
      registerConsumedBracket(PyTokenTypes.LBRACE);
      parseCommaSeparatedPatterns(this::parseKeyValuePatternOrDoubleStarPattern);
      if (checkMatches(PyTokenTypes.RBRACE, PyParsingBundle.message("PARSE.expected.rbrace"))) {
        registerConsumedBracket(PyTokenTypes.RBRACE);
      }
      mark.done(PyElementTypes.MAPPING_PATTERN);
      return true;
    }
    return false;
  }

  private boolean parseKeyValuePatternOrDoubleStarPattern() {
    if (parseDoubleStarPattern()) {
      consumeIllegalLeftoverExpressionTokens();
      return true;
    }
    // The grammar allows only literal or value patterns here, but for recovery purposes,
    // it's easier to match any pattern, as in dict literals, and then report illegal ones
    // in an annotator.
    SyntaxTreeBuilder.Marker mark = myBuilder.mark();
    if (parsePattern()) {
      if (!checkMatches(PyTokenTypes.COLON, PyParsingBundle.message("PARSE.expected.colon"))) {
        mark.drop();
        return true;
      }
      if (!parsePattern()) {
        myBuilder.error(PyParsingBundle.message("PARSE.expected.pattern"));
      }
      mark.done(PyElementTypes.KEY_VALUE_PATTERN);
      return true;
    }
    mark.drop();
    return false;
  }

  private boolean parseDoubleStarPattern() {
    if (atToken(PyTokenTypes.EXP)) {
      SyntaxTreeBuilder.Marker mark = myBuilder.mark();
      nextToken();
      if (!parseCapturePattern()) {
        myBuilder.error(PyParsingBundle.message("PARSE.expected.name"));
      }
      mark.done(PyElementTypes.DOUBLE_STAR_PATTERN);
      return true;
    }
    return false;
  }

  private boolean parseSingleStarPattern() {
    if (atToken(PyTokenTypes.MULT)) {
      SyntaxTreeBuilder.Marker mark = myBuilder.mark();
      nextToken();
      if (!parseWildcardPattern() && !parseCapturePattern()) {
        myBuilder.error(PyParsingBundle.message("PARSE.expected.name.or.wildcard"));
      }
      mark.done(PyElementTypes.SINGLE_STAR_PATTERN);
      return true;
    }
    return false;
  }

  private boolean parseGroupOrParenthesizedSequencePattern() {
    if (atToken(PyTokenTypes.LPAR)) {
      SyntaxTreeBuilder.Marker mark = myBuilder.mark();
      nextToken();
      registerConsumedBracket(PyTokenTypes.LPAR);
      CommaSeparation result = parseCommaSeparatedPatterns(this::parsePattern);
      if (checkMatches(PyTokenTypes.RPAR, PyParsingBundle.message("PARSE.expected.rpar"))) {
        registerConsumedBracket(PyTokenTypes.RPAR);
      }
      mark.done(result == CommaSeparation.SINGLE_NO_COMMA ? PyElementTypes.GROUP_PATTERN : PyElementTypes.SEQUENCE_PATTERN);
      return true;
    }
    return false;
  }

  private boolean parseSequencePatternInBrackets() {
    if (atToken(PyTokenTypes.LBRACKET)) {
      SyntaxTreeBuilder.Marker mark = myBuilder.mark();
      nextToken();
      registerConsumedBracket(PyTokenTypes.LBRACKET);
      parseCommaSeparatedPatterns(this::parsePattern);
      if (checkMatches(PyTokenTypes.RBRACKET, PyParsingBundle.message("PARSE.expected.rbracket"))) {
        registerConsumedBracket(PyTokenTypes.RBRACKET);
      }
      mark.done(PyElementTypes.SEQUENCE_PATTERN);
      return true;
    }
    return false;
  }

  private enum CommaSeparation {
    NO_ELEMENTS,
    SINGLE_NO_COMMA,
    EXISTS,
  }

  private @NotNull CommaSeparation parseCommaSeparatedPatterns(@NotNull BooleanSupplier patternParser) {
    CommaSeparation result = CommaSeparation.NO_ELEMENTS;
    boolean afterPattern = patternParser.getAsBoolean();
    if (afterPattern || atToken(PyTokenTypes.COMMA)) {
      result = CommaSeparation.SINGLE_NO_COMMA;
      while (atToken(PyTokenTypes.COMMA)) {
        result = CommaSeparation.EXISTS;
        if (!afterPattern) {
          myBuilder.error(PyParsingBundle.message("PARSE.expected.pattern"));
        }
        nextToken();
        afterPattern = patternParser.getAsBoolean();
        if (!afterPattern && !atToken(PyTokenTypes.COMMA)) {
          break;
        }
      }
    }
    return result;
  }

  private boolean parseWildcardPattern() {
    if (atToken(PyTokenTypes.IDENTIFIER, "_")) {
      IElementType nextToken = myBuilder.lookAhead(1);
      if (nextToken == PyTokenTypes.DOT || nextToken == PyTokenTypes.LPAR) {
        return false;
      }
      buildTokenElement(PyElementTypes.WILDCARD_PATTERN, myBuilder);
      return true;
    }
    return false;
  }

  private boolean parseCapturePattern() {
    if (atSimpleNameNotUnderscore()) {
      SyntaxTreeBuilder.Marker mark = myBuilder.mark();
      buildTokenElement(PyElementTypes.TARGET_EXPRESSION, myBuilder);
      mark.done(PyElementTypes.CAPTURE_PATTERN);
      return true;
    }
    return false;
  }

  private boolean atSimpleNameNotUnderscore() {
    if (!atToken(PyTokenTypes.IDENTIFIER) || "_".equals(myBuilder.getTokenText())) {
      return false;
    }
    IElementType afterName = myBuilder.lookAhead(1);
    return afterName != PyTokenTypes.DOT && afterName != PyTokenTypes.LPAR;
  }

  private boolean parseValuePattern() {
    if (atToken(PyTokenTypes.IDENTIFIER) && myBuilder.lookAhead(1) == PyTokenTypes.DOT) {
      SyntaxTreeBuilder.Marker mark = myBuilder.mark();
      parseReferenceExpression();
      mark.done(PyElementTypes.VALUE_PATTERN);
      return true;
    }
    return false;
  }

  /** @noinspection UnusedReturnValue*/
  private boolean parseReferenceExpression() {
    if (atToken(PyTokenTypes.IDENTIFIER)) {
      SyntaxTreeBuilder.Marker refExpr = myBuilder.mark();
      nextToken();
      refExpr.done(PyElementTypes.REFERENCE_EXPRESSION);
      while (matchToken(PyTokenTypes.DOT)) {
        refExpr = refExpr.precede();
        checkMatches(PyTokenTypes.IDENTIFIER, PyParsingBundle.message("PARSE.expected.name"));
        refExpr.done(PyElementTypes.REFERENCE_EXPRESSION);
      }
      return true;
    }
    return false;
  }

  private boolean parseLiteralPattern() {
    final SyntaxTreeBuilder.Marker marker = myBuilder.mark();
    if (parseAllowedLiteralExpression()) {
      marker.done(PyElementTypes.LITERAL_PATTERN);
      return true;
    }
    marker.drop();
    return false;
  }

  private boolean parseAllowedLiteralExpression() {
    if (atAnyOfTokens(PyTokenTypes.STRING_NODES)) {
      SyntaxTreeBuilder.Marker stringLiteral = myBuilder.mark();
      while (atAnyOfTokens(PyTokenTypes.STRING_NODES)) {
        nextToken();
      }
      stringLiteral.done(PyElementTypes.STRING_LITERAL_EXPRESSION);
      return true;
    }
    else if (atAnyOfTokens(PyTokenTypes.TRUE_KEYWORD, PyTokenTypes.FALSE_KEYWORD)) {
      buildTokenElement(PyElementTypes.BOOL_LITERAL_EXPRESSION, myBuilder);
      return true;
    }
    else if (atToken(PyTokenTypes.NONE_KEYWORD)) {
      buildTokenElement(PyElementTypes.NONE_LITERAL_EXPRESSION, myBuilder);
      return true;
    }
    else if (atAnyOfTokens(PyTokenTypes.PLUS, PyTokenTypes.MINUS) || atAnyOfTokens(PyTokenTypes.NUMERIC_LITERALS)) {
      parseComplexNumber();
      return true;
    }
    return false;
  }

  private void parseComplexNumber() {
    SyntaxTreeBuilder.Marker complexLiteral = myBuilder.mark();
    if (atAnyOfTokens(PyTokenTypes.PLUS, PyTokenTypes.MINUS)) {
      SyntaxTreeBuilder.Marker prefixExpression = myBuilder.mark();
      nextToken();
      if (!parseNumericLiteral()) {
        myBuilder.error(PyParsingBundle.message("PARSE.expected.number"));
      }
      prefixExpression.done(PyElementTypes.PREFIX_EXPRESSION);
    }
    else {
      parseNumericLiteral();
    }
    if (atAnyOfTokens(PyTokenTypes.PLUS, PyTokenTypes.MINUS)) {
      nextToken();
      if (!parseNumericLiteral()) {
        myBuilder.error(PyParsingBundle.message("PARSE.expected.number"));
      }
      complexLiteral.done(PyElementTypes.BINARY_EXPRESSION);
    }
    else {
      complexLiteral.drop();
    }
  }

  private boolean parseNumericLiteral() {
    if (atToken(PyTokenTypes.INTEGER_LITERAL)) {
      buildTokenElement(PyElementTypes.INTEGER_LITERAL_EXPRESSION, myBuilder);
      return true;
    }
    else if (atToken(PyTokenTypes.FLOAT_LITERAL)) {
      buildTokenElement(PyElementTypes.FLOAT_LITERAL_EXPRESSION, myBuilder);
      return true;
    }
    else if (atToken(PyTokenTypes.IMAGINARY_LITERAL)) {
      buildTokenElement(PyElementTypes.IMAGINARY_LITERAL_EXPRESSION, myBuilder);
      return true;
    }
    return false;
  }

  private void consumeIllegalLeftoverExpressionTokens() {
    SyntaxTreeBuilder.Marker mark = myBuilder.mark();
    int initialBalance = myPendingClosingBraces.size();
    boolean consumedAny = false;
    while (!myBuilder.eof() && !atAnyOfTokens(PyTokenTypes.IF_KEYWORD,
                                              PyTokenTypes.COLON,
                                              PyTokenTypes.AS_KEYWORD,
                                              PyTokenTypes.OR,
                                              PyTokenTypes.STATEMENT_BREAK)) {
      IElementType token = myBuilder.getTokenType();
      if (myPendingClosingBraces.size() == initialBalance &&
          (myPendingClosingBraces.contains(token) || token == PyTokenTypes.COMMA)) {
        break;
      }
      nextToken();
      consumedAny = true;
      if (PyTokenTypes.ALL_BRACES.contains(token)) {
        registerConsumedBracket(token);
      }
    }
    if (consumedAny) {
      mark.error(PyParsingBundle.message("unexpected.tokens"));
    }
    else {
      mark.drop();
    }
  }

  private void registerConsumedBracket(@NotNull IElementType bracketType) {
    if (PyTokenTypes.OPEN_BRACES.contains(bracketType)) {
      myPendingClosingBraces.push(bracketType == PyTokenTypes.LBRACE ? PyTokenTypes.RBRACE :
                                  bracketType == PyTokenTypes.LBRACKET ? PyTokenTypes.RBRACKET :
                                  bracketType == PyTokenTypes.LPAR ? PyTokenTypes.RPAR : null);
    }
    else if (PyTokenTypes.CLOSE_BRACES.contains(bracketType) && myPendingClosingBraces.contains(bracketType)) {
      while (myPendingClosingBraces.peek() != bracketType) {
        myPendingClosingBraces.pop();
      }
      IElementType expected = myPendingClosingBraces.pop();
      assert expected == bracketType;
    }
  }
}
