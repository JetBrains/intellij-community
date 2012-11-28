/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 03.01.11
*/
public class XPath2Parser extends XPathParser {
  @Override
  protected boolean parseExpr(PsiBuilder builder) {
    final PsiBuilder.Marker seq = builder.mark();
    final boolean b = parseExprSingle(builder);
    if (b && builder.getTokenType() == XPathTokenTypes.COMMA) {
      do {
        builder.advanceLexer();
        if (!parseExprSingle(builder)) {
          builder.error("Expression expected");
        }
      } while (builder.getTokenType() == XPathTokenTypes.COMMA);
      seq.done(XPath2ElementTypes.SEQUENCE);
    } else {
      seq.drop();
    }
    return b;
  }

  @Override
  protected boolean parseParenExpr(PsiBuilder builder) {
    if (builder.getTokenType() == XPathTokenTypes.RPAREN) {
      builder.mark().done(XPath2ElementTypes.SEQUENCE);
      return true;
    }
    return super.parseParenExpr(builder);
  }

  @Override
  protected boolean parsePrimaryExpr(PsiBuilder builder) {
    if (builder.getTokenType() == XPathTokenTypes.DOT) {
      PsiBuilder.Marker mark = builder.mark();
      builder.advanceLexer();
      mark.done(XPath2ElementTypes.CONTEXT_ITEM);
      return true;
    } else {
      return super.parsePrimaryExpr(builder);
    }
  }

  private boolean parseExprSingle(PsiBuilder builder) {
    if (builder.getTokenType() == XPath2TokenTypes.FOR) {
      return parseForExpr(builder);
    } else if (XPath2TokenTypes.QUANTIFIERS.contains(builder.getTokenType())) {
      return parseQuantifiedExpr(builder);
    } else if (builder.getTokenType() == XPath2TokenTypes.IF) {
      return parseIfExpr(builder);
    }
    return parseOrExpr(builder);
  }

  @Override
  protected boolean parseArgument(PsiBuilder builder) {
    return parseExprSingle(builder);
  }

  private boolean parseIfExpr(PsiBuilder builder) {
    final PsiBuilder.Marker mark = builder.mark();
    checkMatches(builder, XPath2TokenTypes.IF, "'if' expected");
    checkMatches(builder, XPathTokenTypes.LPAREN, "'(' expected");
    if (!parseExpr(builder)) {
      builder.error("expression expected");
    }
    checkMatches(builder, XPathTokenTypes.RPAREN, "')' expected");
    checkMatches(builder, XPath2TokenTypes.THEN, "'then' expected");
    if (!parseExprSingle(builder)) {
      builder.error("expression expected");
    }
    checkMatches(builder, XPath2TokenTypes.ELSE, "'else' expected");
    if (!parseExprSingle(builder)) {
      builder.error("expression expected");
    }
    mark.done(XPath2ElementTypes.IF);
    return true;
  }

  private boolean parseQuantifiedExpr(PsiBuilder builder) {
    PsiBuilder.Marker mark = builder.mark();
    checkMatches(builder, XPath2TokenTypes.QUANTIFIERS, "'every' or 'some' expected");

    parseBindingSequence(builder);

    checkMatches(builder, XPath2TokenTypes.SATISFIES, "'satisfies' expected");

    final PsiBuilder.Marker ret = builder.mark();
    if (!parseExprSingle(builder)) {
      builder.error("expression expected");
    }

    ret.done(XPath2ElementTypes.BODY);
    mark.done(XPath2ElementTypes.QUANTIFIED);
    return true;
  }

  protected boolean parseForExpr(PsiBuilder builder) {
    final PsiBuilder.Marker mark = builder.mark();
    checkMatches(builder, XPath2TokenTypes.FOR, "'for' expected");

    parseBindingSequence(builder);

    final PsiBuilder.Marker ret = builder.mark();
    checkMatches(builder, XPath2TokenTypes.RETURN, "'return' expected");
    if (!parseExprSingle(builder)) {
      builder.error("expression expected");
    }

    ret.done(XPath2ElementTypes.BODY);
    mark.done(XPath2ElementTypes.FOR);
    return true;
  }

  private void parseBindingSequence(PsiBuilder builder) {
    do {
      if (builder.getTokenType() == XPathTokenTypes.COMMA) {
        builder.advanceLexer();
      }

      final PsiBuilder.Marker bindingSeq = builder.mark();
      parseVariableDecl(builder);

      checkMatches(builder, XPath2TokenTypes.IN, "'in' expected");

      if (!parseExprSingle(builder)) {
        builder.error("expression expected");
      }
      bindingSeq.done(XPath2ElementTypes.BINDING_SEQ);
    } while (builder.getTokenType() == XPathTokenTypes.COMMA);
  }

  @Override
  protected boolean parseEqualityExpression(PsiBuilder builder) {
    return parseComparisonExpr(builder);
  }

  private boolean parseComparisonExpr(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseRangeExpression(builder)) {
      expr.drop();
      return false;
    }

    while (XPath2TokenTypes.COMP_OPS.contains(builder.getTokenType())) {
      makeToken(builder);
      if (!parseRangeExpression(builder)) {
        builder.error("expression expected");
      }
      expr.done(XPathElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  protected boolean parseRangeExpression(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseAdditiveExpression(builder)) {
      expr.drop();
      return false;
    }

    while (builder.getTokenType() == XPath2TokenTypes.TO) {
      makeToken(builder);
      if (!parseAdditiveExpression(builder)) {
        builder.error("expression expected");
      }
      expr.done(XPath2ElementTypes.RANGE_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  @Override
  protected boolean parseMultiplicativeExpression(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseUnionExpression(builder)) {
      expr.drop();
      return false;
    }

    while (XPath2TokenTypes.MULT_OPS.contains(builder.getTokenType())) {
      makeToken(builder);
      if (!parseUnionExpression(builder)) {
        builder.error("expression expected");
      }
      expr.done(XPathElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  @Override
  protected boolean parseUnionExpression(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseIntersectExceptExpr(builder)) {
      expr.drop();
      return false;
    }

    while (XPath2TokenTypes.UNION_OPS.contains(builder.getTokenType())) {
      makeToken(builder);
      if (!parseIntersectExceptExpr(builder)) {
        builder.error("expression expected");
      }
      expr.done(XPathElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  protected boolean parseIntersectExceptExpr(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseInstanceofExpr(builder)) {
      expr.drop();
      return false;
    }

    while (XPath2TokenTypes.INTERSECT_EXCEPT.contains(builder.getTokenType())) {
      makeToken(builder);
      if (!parseInstanceofExpr(builder)) {
        builder.error("expression expected");
      }
      expr.done(XPathElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  protected boolean parseInstanceofExpr(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseTreatExpr(builder)) {
      expr.drop();
      return false;
    }

    if (builder.getTokenType() == XPath2TokenTypes.INSTANCE) {
      builder.advanceLexer();
      checkMatches(builder, XPath2TokenTypes.OF, "'of' expected");
      if (!parseSequenceType(builder)) {
        builder.error("sequence type expected");
      }
      expr.done(XPath2ElementTypes.INSTANCE_OF);
    } else {
      expr.drop();
    }

    return true;
  }

  protected boolean parseTreatExpr(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseCastableExpr(builder)) {
      expr.drop();
      return false;
    }

    if (builder.getTokenType() == XPath2TokenTypes.TREAT) {
      builder.advanceLexer();

      checkMatches(builder, XPath2TokenTypes.AS, "'as' expected");
      if (!parseSequenceType(builder)) {
        builder.error("sequence type expected");
      }
      expr.done(XPath2ElementTypes.TREAT_AS);
    } else {
      expr.drop();
    }

    return true;
  }

  protected boolean parseCastableExpr(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseCastExpr(builder)) {
      expr.drop();
      return false;
    }

    if (builder.getTokenType() == XPath2TokenTypes.CASTABLE) {
      builder.advanceLexer();

      checkMatches(builder, XPath2TokenTypes.AS, "'as' expected");
      parseSingleType(builder);
      expr.done(XPath2ElementTypes.CASTABLE_AS);
    } else {
      expr.drop();
    }

    return true;
  }

  protected boolean parseCastExpr(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseUnaryExpression(builder)) {
      expr.drop();
      return false;
    }

    if (builder.getTokenType() == XPath2TokenTypes.CAST) {
      builder.advanceLexer();

      checkMatches(builder, XPath2TokenTypes.AS, "'as' expected");
      parseSingleType(builder);
      expr.done(XPath2ElementTypes.CAST_AS);
    } else {
      expr.drop();
    }

    return true;
  }

  private static void parseSingleType(PsiBuilder builder) {
    PsiBuilder.Marker mark = builder.mark();
    if (!parseQName(builder)) {
      builder.error("QName expected");
    }
    if (builder.getTokenType() == XPath2TokenTypes.QUEST) {
      builder.advanceLexer();
    }
    mark.done(XPath2ElementTypes.SINGLE_TYPE);

    if (builder.getTokenType() == XPathTokenTypes.STAR) {
      builder.remapCurrentToken(XPathTokenTypes.MULT);
    }
  }

  private boolean parseSequenceType(PsiBuilder builder) {
    PsiBuilder.Marker mark = builder.mark();

    final IElementType type = parseItemOrEmptySequenceType(builder);
    if (type != null) {
      if (type == XPath2TokenTypes.ITEM) {
        if (XPath2TokenTypes.OCCURRENCE_OPS.contains(builder.getTokenType())) {
          makeToken(builder);
        }
      }

      mark.done(XPath2ElementTypes.SEQUENCE_TYPE);
      return true;
    } else if (parseNodeType(builder) || parseQName(builder)) {
      if (builder.getTokenType() == XPathTokenTypes.MULT) {
        builder.remapCurrentToken(XPathTokenTypes.STAR);
      }
      if (XPath2TokenTypes.OCCURRENCE_OPS.contains(builder.getTokenType())) {
        makeToken(builder);
      }
      mark.done(XPath2ElementTypes.SEQUENCE_TYPE);
      return true;
    }

    mark.drop();
    return false;
  }

  @Nullable
  private IElementType parseItemOrEmptySequenceType(PsiBuilder builder) {
    final IElementType tokenType = builder.getTokenType();
    if (tokenType == XPath2TokenTypes.ITEM || tokenType == XPath2TokenTypes.EMPTY_SEQUENCE) {
      final PsiBuilder.Marker mark = builder.mark();
      builder.advanceLexer();
      parseArgumentList(builder);
      mark.done(XPath2ElementTypes.ITEM_OR_EMPTY_SEQUENCE);
      return tokenType;
    }
    return null;
  }

  private static boolean parseQName(PsiBuilder builder) {
    if (builder.getTokenType() == XPathTokenTypes.NCNAME) {
      builder.advanceLexer();
      if (builder.getTokenType() == XPathTokenTypes.COL) {
        builder.advanceLexer();
        checkMatches(builder, XPathTokenTypes.NCNAME, "'NCName expected");
      }
      return true;
    }
    return false;
  }

  protected boolean parseUnaryExpression(PsiBuilder builder) {
    if (XPathTokenTypes.ADD_OPS.contains(builder.getTokenType())) {
      PsiBuilder.Marker expr = builder.mark();
      do {
        builder.advanceLexer();

        if (!parseUnaryExpression(builder)) {
          builder.error("Expression expected");
        }
        expr.done(XPathElementTypes.PREFIX_EXPRESSION);
        expr = expr.precede();
      } while (XPathTokenTypes.ADD_OPS.contains(builder.getTokenType()));
      expr.drop();
      return true;
    }
    return parseValueExpression(builder);
  }

  private boolean parseValueExpression(PsiBuilder builder) {
    return parsePathExpression(builder);
  }

  private static void parseVariableDecl(PsiBuilder builder) {
    parseVariable(builder, XPath2ElementTypes.VARIABLE_DECL);
  }

  @Override
  protected boolean parsePathExpression(PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    if (builder.getTokenType() == XPathTokenTypes.PATH) {
      builder.advanceLexer();
      parseRelativePathExpr(builder, false);
      marker.done(XPathElementTypes.LOCATION_PATH);
      return true;
    } else if (builder.getTokenType() == XPathTokenTypes.ANY_PATH) {
      builder.advanceLexer();
      if (!parseRelativePathExpr(builder, false)) {
        builder.error("relative path expected");
      }
      marker.done(XPathElementTypes.LOCATION_PATH);
      return true;
    } else {
      marker.drop();
      return parseRelativePathExpr(builder, true);
    }
  }

  private boolean parseRelativePathExpr(PsiBuilder builder, boolean pathRequired) {
    PsiBuilder.Marker path = builder.mark();
    boolean nameLocationPath = false;

    PsiBuilder.Marker expr = builder.mark();
    if (parseAxisStep(builder)) {
      expr.done(XPathElementTypes.STEP);
      expr = expr.precede();
      if (pathRequired) nameLocationPath = true;
    } else if (!parseFilterExpr(builder)) {
      expr.drop();
      path.drop();
      return false;
    }

    while (XPathTokenTypes.PATH_OPS.contains(builder.getTokenType())) {
      if (pathRequired) nameLocationPath = true;

      makeToken(builder);
      if (!parseStepExpr(builder)) {
        builder.error("expression expected");
      }

      expr.done(XPathElementTypes.STEP);
      expr = expr.precede();
    }

    expr.drop();

    if (nameLocationPath) {
      path.done(XPathElementTypes.LOCATION_PATH);
    } else {
      path.drop();
    }
    return true;
  }

  private boolean parseStepExpr(PsiBuilder builder) {
    return parseFilterExpr(builder) || parseAxisStep(builder);
  }

  private boolean parseAxisStep(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();

    final PsiBuilder.Marker mark = builder.mark();
    if (XPathTokenTypes.AXIS.contains(builder.getTokenType())) {
      builder.advanceLexer();
      mark.done(XPathElementTypes.AXIS_SPECIFIER);
      checkMatches(builder, XPathTokenTypes.COLCOL, ":: expected");
      if (!parseNodeTest(builder)) {
        builder.error("node test expected");
      }
    } else if (builder.getTokenType() == XPathTokenTypes.AT) {
      builder.advanceLexer();
      mark.done(XPathElementTypes.AXIS_SPECIFIER);
      if (!parseNodeTest(builder)) {
        builder.error("node test expected");
      }
    } else if (builder.getTokenType() == XPathTokenTypes.DOTDOT) {
      mark.drop();
      builder.advanceLexer();
    } else {
      mark.done(XPathElementTypes.AXIS_SPECIFIER);

      if (!parseNodeTest(builder)) {
        mark.rollbackTo();
        expr.drop();
        return false;
      }
    }

    while (XPathTokenTypes.LBRACKET == builder.getTokenType()) {
      parsePredicate(builder);

      expr.done(XPathElementTypes.FILTER_EXPRESSION);
      expr = expr.precede();
    }
    expr.drop();

    return true;
  }

  private boolean parseFilterExpr(PsiBuilder builder) {
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

  @Override
  protected boolean parseWildcard(PsiBuilder builder) {
    builder.advanceLexer();

    if (builder.getTokenType() == XPathTokenTypes.COL) {
      builder.advanceLexer();
      if (builder.getTokenType() != XPathTokenTypes.NCNAME) {
        builder.error("NCName expected");
      }
      builder.advanceLexer();
    }

    return true;
  }

  @Override
  protected TokenSet unionOps() {
    return XPath2TokenTypes.UNION_OPS;
  }
}
