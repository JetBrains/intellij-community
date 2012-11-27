/*
 * Copyright 2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class XPathParser implements PsiParser {
  private static final boolean DBG_MODE = Boolean.getBoolean(XPathParser.class.getName() + ".debug") || ApplicationManager.getApplication().isUnitTestMode();

  @NotNull
  public ASTNode parse(IElementType root, PsiBuilder builder) {
    builder.setDebugMode(DBG_MODE);

    final PsiBuilder.Marker rootMarker = builder.mark();
    if (!builder.eof()) {
      boolean avt = false;
      if (builder.getTokenType() == XPathTokenTypes.LBRACE) {
        builder.advanceLexer();
        avt = true;
      }
      if (!parseExpr(builder)) {
        builder.error("XPath expression expected");
      }
      if (avt) {
        checkMatches(builder, XPathTokenTypes.RBRACE, "'}' expected");
      }
      consumeBadTokens(builder, TokenSet.EMPTY);
    }
    rootMarker.done(root);
    return builder.getTreeBuilt();
  }

  protected boolean parsePrimaryExpr(PsiBuilder builder) {
    final IElementType tokenType = builder.getTokenType();

    if (tokenType == XPathTokenTypes.DOLLAR) {
      parseVariable(builder);
    } else if (tokenType == XPathTokenTypes.LPAREN) {
      parseParenthesizedExpr(builder);
    } else if (tokenType == XPathTokenTypes.STRING_LITERAL) {
      parseLiteral(builder);
    } else if (tokenType == XPathTokenTypes.NUMBER) {
      parseNumber(builder);
    } else if (tokenType == XPathTokenTypes.FUNCTION_NAME || tokenType == XPathTokenTypes.EXT_PREFIX) {
      parseFunction(builder);
    } else {
      return false;
    }
    return true;
  }

  private void parseParenthesizedExpr(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    if (!parseParenExpr(builder)) {
      builder.error("expression expected");
    }
    checkMatches(builder, XPathTokenTypes.RPAREN, ") expected");
    marker.done(XPathElementTypes.PARENTHESIZED_EXPR);
  }

  protected boolean parseParenExpr(PsiBuilder builder) {
    return parseExpr(builder);
  }

  protected boolean parseFunction(PsiBuilder builder) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType == XPathTokenTypes.FUNCTION_NAME || tokenType == XPathTokenTypes.EXT_PREFIX) {
      final PsiBuilder.Marker func = builder.mark();
      builder.advanceLexer();
      if (builder.getTokenType() == XPathTokenTypes.COL) {
        builder.advanceLexer();
        checkMatches(builder, XPathTokenTypes.FUNCTION_NAME, "function name expected");
      }
      parseArgumentList(builder);

      func.done(XPathElementTypes.FUNCTION_CALL);
      return true;
    }
    return false;
  }

  protected void parseArgumentList(PsiBuilder builder) {
    checkMatches(builder, XPathTokenTypes.LPAREN, "( expected");

    if (builder.getTokenType() != XPathTokenTypes.RPAREN) {
      if (!parseArgument(builder)) {
        builder.error("expression expected");
      }
    }
    while (builder.getTokenType() == XPathTokenTypes.COMMA) {
      builder.advanceLexer();
      if (!parseArgument(builder)) {
        builder.error("expression expected");
      }
    }
    checkMatches(builder, XPathTokenTypes.RPAREN, ") expected");
  }

  protected boolean parseArgument(PsiBuilder builder) {
    return parseExpr(builder);
  }

  private static void parseNumber(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    marker.done(XPathElementTypes.NUMBER);
  }

  private static void parseLiteral(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    marker.done(XPathElementTypes.STRING);
  }

  private static void parseVariable(PsiBuilder builder) {
    parseVariable(builder, XPathElementTypes.VARIABLE_REFERENCE);
  }

  protected static void parseVariable(PsiBuilder builder, IElementType elementType) {
    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() == XPathTokenTypes.VARIABLE_PREFIX) {
      builder.advanceLexer();
      checkMatches(builder, XPathTokenTypes.COL, "':' expected");
    }
    checkMatches(builder, XPathTokenTypes.VARIABLE_NAME, "variable expected");
    marker.done(elementType);
  }

  protected boolean parseExpr(PsiBuilder builder) {
    return parseOrExpr(builder);
  }

  protected boolean parseOrExpr(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseAndExpression(builder)) {
      expr.drop();
      return false;
    }

    while (builder.getTokenType() == XPathTokenTypes.OR) {
      makeToken(builder);
      if (!parseAndExpression(builder)) {
        builder.error("expression expected");
      }
      expr.done(XPathElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  protected boolean parseAndExpression(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseEqualityExpression(builder)) {
      expr.drop();
      return false;
    }

    while (builder.getTokenType() == XPathTokenTypes.AND) {
      makeToken(builder);
      if (!parseEqualityExpression(builder)) {
        builder.error("expression expected");
      }
      expr.done(XPathElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  protected boolean parseEqualityExpression(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseRelationalExpression(builder)) {
      expr.drop();
      return false;
    }

    while (XPathTokenTypes.EQUALITY_OPS.contains(builder.getTokenType())) {
      makeToken(builder);
      if (!parseRelationalExpression(builder)) {
        builder.error("expression expected");
      }
      expr.done(XPathElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;

  }

  private boolean parseRelationalExpression(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseAdditiveExpression(builder)) {
      expr.drop();
      return false;
    }

    while (XPathTokenTypes.REL_OPS.contains(builder.getTokenType())) {
      makeToken(builder);
      if (!parseAdditiveExpression(builder)) {
        builder.error("expression expected");
      }
      expr.done(XPathElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;

  }

  protected boolean parseAdditiveExpression(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseMultiplicativeExpression(builder)) {
      expr.drop();
      return false;
    }

    while (XPathTokenTypes.ADD_OPS.contains(builder.getTokenType())) {
      makeToken(builder);
      if (!parseMultiplicativeExpression(builder)) {
        builder.error("expression expected");
      }
      expr.done(XPathElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;

  }

  protected boolean parseMultiplicativeExpression(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseUnaryExpression(builder)) {
      expr.drop();
      return false;
    }

    while (XPathTokenTypes.MUL_OPS.contains(builder.getTokenType())) {
      makeToken(builder);
      if (!parseUnaryExpression(builder)) {
        builder.error("expression expected");
      }
      expr.done(XPathElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;

  }

  protected boolean parseUnaryExpression(PsiBuilder builder) {
    if (builder.getTokenType() == XPathTokenTypes.MINUS) {
      final PsiBuilder.Marker expr = builder.mark();
      builder.advanceLexer();
      if (!parseUnionExpression(builder)) {
        builder.error("Expression expected");
      }
      expr.done(XPathElementTypes.PREFIX_EXPRESSION);
      return true;
    } else {
      return parseUnionExpression(builder);
    }
  }

  protected boolean parseUnionExpression(final PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parsePathExpression(builder)) {
      expr.drop();
      return false;
    }

    while (unionOps().contains(builder.getTokenType())) {
      makeToken(builder);
      if (!parsePathExpression(builder)) {
        builder.error("expression expected");
      }
      expr.done(XPathElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  protected TokenSet unionOps() {
    return TokenSet.create(XPathTokenTypes.UNION);
  }

  /**
   * [19]    PathExpr    ::=    LocationPath
   * | FilterExpr
   * | FilterExpr '/' RelativeLocationPath
   * | FilterExpr '//' RelativeLocationPath
   */
  protected boolean parsePathExpression(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();

    if (!parseLocationPath(builder, false) && !parseAbsoluteLocationPath(builder)) {
      final PsiBuilder.Marker m2 = builder.mark();
      if (!parseFilterExpression(builder)) {
        m2.drop();
        marker.drop();
        return false;
      }

      if (XPathTokenTypes.PATH_OPS.contains(builder.getTokenType())) {
        makeToken(builder);
        if (!parseLocationPath(builder, false, m2)) {
          builder.error("location path expected");
        }
        marker.done(XPathElementTypes.LOCATION_PATH);
      } else {
        m2.drop();
        marker.drop();
      }
    } else {
      marker.done(XPathElementTypes.LOCATION_PATH);
    }

    return true;
  }

  protected static void makeToken(PsiBuilder builder) {
    final IElementType tokenType = builder.getTokenType();
    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    marker.done(tokenType);
  }

  /**
   * [3]    RelativeLocationPath    ::=    Step
   * | RelativeLocationPath '/' Step
   * | RelativeLocationPath '//' Step
   */
  private boolean parseLocationPath(PsiBuilder builder, boolean isAbsolute, PsiBuilder.Marker... m) {
    assert m.length <= 1;

    PsiBuilder.Marker marker = m.length == 1 ? m[0] : builder.mark();
    if (isAbsolute) {
      // root step
      makeToken(builder);
      if (builder.getTokenType() == null) {
        marker.done(XPathElementTypes.STEP);
        return true;
      }
    }
    if (!parseStep(builder)) {
      marker.drop();
      return false;
    }
    marker.done(XPathElementTypes.STEP);
    marker = marker.precede();

    if (XPathTokenTypes.PATH_OPS.contains(builder.getTokenType())) {
      do {
        makeToken(builder);
        if (!parseStep(builder)) {
          builder.error("location step expected");
        }
        marker.done(XPathElementTypes.STEP);
        marker = marker.precede();
      } while (XPathTokenTypes.PATH_OPS.contains(builder.getTokenType()));
    }

    marker.drop();
    return true;
  }

  /**
   * [4]    Step    ::=    AxisSpecifier NodeTest Predicate*
   * | AbbreviatedStep
   */
  private boolean parseStep(PsiBuilder builder) {
    if (parseAxisSpecifier(builder)) {
      if (!parseNodeTest(builder)) {
        builder.error("node test expected");
      }
      while (builder.getTokenType() == XPathTokenTypes.LBRACKET) {
        parsePredicate(builder);
      }

      return true;
    } else if (parseAbbreviatedStep(builder)) {
      return true;
    }
    return false;
  }

  /**
   * [12]    AbbreviatedStep    ::=    '.' | '..'
   */
  private static boolean parseAbbreviatedStep(PsiBuilder builder) {
    if (builder.getTokenType() == XPathTokenTypes.DOT || builder.getTokenType() == XPathTokenTypes.DOTDOT) {
      makeToken(builder);
      return true;
    }
    return false;
  }

  /**
   * [7]    NodeTest    ::=    NameTest | NodeType '(' ')' | 'processing-instruction' '(' Literal ')'
   */
  protected boolean parseNodeTest(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    if (!parseNameTest(builder)) {
      if (!parseNodeType(builder)) {
        marker.drop();
        return false;
      }
    }
    marker.done(XPathElementTypes.NODE_TEST);
    return true;
  }

  protected boolean parseNodeType(PsiBuilder builder) {
    if (builder.getTokenType() == XPathTokenTypes.NODE_TYPE) {
      final PsiBuilder.Marker m = builder.mark();
      builder.advanceLexer();
      parseArgumentList(builder);
      m.done(XPathElementTypes.NODE_TYPE);
      return true;
    }
    return false;
  }

  /**
   * [37]    NameTest    ::=    '*' | NCName ':' '*' | QName
   */
  protected boolean parseNameTest(PsiBuilder builder) {
    if (builder.getTokenType() == XPathTokenTypes.STAR) {
      return parseWildcard(builder);
    } else if (builder.getTokenType() == XPathTokenTypes.NCNAME) {
      builder.advanceLexer();

      if (builder.getTokenType() == XPathTokenTypes.COL) {
        builder.advanceLexer();
        if (builder.getTokenType() != XPathTokenTypes.STAR) {
          if (builder.getTokenType() != XPathTokenTypes.NCNAME) {
            builder.error("* or NCName expected");
          } else {
            builder.advanceLexer();
          }
        } else {
          builder.advanceLexer();
        }
      }
      return true;
    }
    return false;
  }

  protected boolean parseWildcard(PsiBuilder builder) {
    builder.advanceLexer();
    return true;
  }

  /**
   * [5]    AxisSpecifier    ::=    AxisName '::' | AbbreviatedAxisSpecifier
   * <p/>
   * [13]   AbbreviatedAxisSpecifier    ::=    '@'?
   */
  private boolean parseAxisSpecifier(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    final IElementType tokenType = builder.getTokenType();
    if (XPathTokenTypes.AXIS.contains(tokenType)) {
      builder.advanceLexer();
      checkMatches(builder, XPathTokenTypes.COLCOL, ":: expected");
    } else {
      if (tokenType == XPathTokenTypes.AT) {
        builder.advanceLexer();
      } else if (tokenType == XPathTokenTypes.DOT || tokenType == XPathTokenTypes.DOTDOT) {
        marker.drop();
        return false;
      } else {
        final PsiBuilder.Marker m = builder.mark();
        final boolean b = parseNodeTest(builder);
        m.rollbackTo();
        if (!b) {
          marker.drop();
          return false;
        }
      }
    }
    marker.done(XPathElementTypes.AXIS_SPECIFIER);
    return true;
  }

  /**
   * [20]    FilterExpr    ::=    PrimaryExpr | FilterExpr Predicate
   */
  private boolean parseFilterExpression(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parsePrimaryExpr(builder)) {
      expr.drop();
      return false;
    }

    while (XPathTokenTypes.LBRACKET == builder.getTokenType()) {
      parsePredicate(builder);

      expr.done(XPathElementTypes.FILTER_EXPRESSION);
      expr = expr.precede();
    }
    expr.drop();

    return true;
  }

  /**
   * [8]    Predicate         ::=    '[' PredicateExpr ']'
   * [9]    PredicateExpr     ::=    Expr
   */
  protected boolean parsePredicate(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    if (builder.getTokenType() != XPathTokenTypes.LBRACKET) {
      marker.drop();
      return false;
    }
    builder.advanceLexer();
    if (!parseExpr(builder)) {
      builder.error("expression expected");
    }
    checkMatches(builder, XPathTokenTypes.RBRACKET, "] expected");
    marker.done(XPathElementTypes.PREDICATE);
    return true;
  }

  /**
   * [2]    AbsoluteLocationPath    ::=    '/' RelativeLocationPath? | AbbreviatedAbsoluteLocationPath
   */
  private boolean parseAbsoluteLocationPath(PsiBuilder builder) {
    if (builder.getTokenType() == XPathTokenTypes.PATH) {
      parseLocationPath(builder, true);
      return true;
    } else {
      return parseAbbreviatedAbsoluteLocationPath(builder);
    }
  }

  /**
   * [10]    AbbreviatedAbsoluteLocationPath    ::=    '//' RelativeLocationPath
   */
  private boolean parseAbbreviatedAbsoluteLocationPath(PsiBuilder builder) {
    if (builder.getTokenType() == XPathTokenTypes.ANY_PATH) {
      if (!parseLocationPath(builder, true)) {
        builder.error("location path expected");
      }
      return true;
    }
    return false;
  }

  private static void consumeBadTokens(PsiBuilder builder, TokenSet nextAccepted) {
    if (nextAccepted.getTypes().length == 0 && builder.eof()) {
      return;
    }
    if (!nextAccepted.contains(builder.getTokenType())) {
      builder.error("Unexpected token");
      do {
        if (builder.eof()) {
          builder.error("Unexpected end of file");
          break;
        }
        builder.advanceLexer();
      } while (!nextAccepted.contains(builder.getTokenType()));
    }
  }

  protected static void checkMatches(final PsiBuilder builder, final IElementType token, final String message) {
    if (builder.getTokenType() == token) {
      builder.advanceLexer();
    } else {
      builder.error(message);
    }
  }

  protected static void checkMatches(final PsiBuilder builder, final TokenSet tokens, final String message) {
    if (tokens.contains(builder.getTokenType())) {
      builder.advanceLexer();
    } else {
      builder.error(message);
    }
  }
}
