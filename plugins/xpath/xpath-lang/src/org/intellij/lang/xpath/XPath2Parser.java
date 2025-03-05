// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.Nullable;

public class XPath2Parser extends XPathParser {
  @Override
  protected boolean parseExpr(PsiBuilder builder) {
    final PsiBuilder.Marker seq = builder.mark();
    final boolean b = parseExprSingle(builder);
    if (b && builder.getTokenType() == XPathTokenTypes.COMMA) {
      do {
        builder.advanceLexer();
        if (!parseExprSingle(builder)) {
          builder.error(XPathBundle.message("parsing.error.expression.expected"));
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
    checkMatches(builder, XPath2TokenTypes.IF, XPathBundle.message("parsing.error.if.expected"));
    checkMatches(builder, XPathTokenTypes.LPAREN, XPathBundle.message("parsing.error.opening.parenthesis.expected"));
    if (!parseExpr(builder)) {
      builder.error(XPathBundle.message("parsing.error.expression.expected"));
    }
    checkMatches(builder, XPathTokenTypes.RPAREN, XPathBundle.message("parsing.error.closing.parenthesis.expected"));
    checkMatches(builder, XPath2TokenTypes.THEN, XPathBundle.message("parsing.error.then.expected"));
    if (!parseExprSingle(builder)) {
      builder.error(XPathBundle.message("parsing.error.expression.expected"));
    }
    checkMatches(builder, XPath2TokenTypes.ELSE, XPathBundle.message("parsing.error.else.expected"));
    if (!parseExprSingle(builder)) {
      builder.error(XPathBundle.message("parsing.error.expression.expected"));
    }
    mark.done(XPath2ElementTypes.IF);
    return true;
  }

  private boolean parseQuantifiedExpr(PsiBuilder builder) {
    PsiBuilder.Marker mark = builder.mark();
    checkMatches(builder, XPath2TokenTypes.QUANTIFIERS, XPathBundle.message("parsing.error.every.or.some.expected"));

    parseBindingSequence(builder);

    checkMatches(builder, XPath2TokenTypes.SATISFIES, XPathBundle.message("parsing.error.satisfies.expected"));

    final PsiBuilder.Marker ret = builder.mark();
    if (!parseExprSingle(builder)) {
      builder.error(XPathBundle.message("parsing.error.expression.expected"));
    }

    ret.done(XPath2ElementTypes.BODY);
    mark.done(XPath2ElementTypes.QUANTIFIED);
    return true;
  }

  protected boolean parseForExpr(PsiBuilder builder) {
    final PsiBuilder.Marker mark = builder.mark();
    checkMatches(builder, XPath2TokenTypes.FOR, XPathBundle.message("parsing.error.for.expected"));

    parseBindingSequence(builder);

    final PsiBuilder.Marker ret = builder.mark();
    checkMatches(builder, XPath2TokenTypes.RETURN, XPathBundle.message("parsing.error.return.expected"));
    if (!parseExprSingle(builder)) {
      builder.error(XPathBundle.message("parsing.error.expression.expected"));
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

      checkMatches(builder, XPath2TokenTypes.IN, XPathBundle.message("parsing.error.in.expected"));

      if (!parseExprSingle(builder)) {
        builder.error(XPathBundle.message("parsing.error.expression.expected"));
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
        builder.error(XPathBundle.message("parsing.error.expression.expected"));
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
        builder.error(XPathBundle.message("parsing.error.expression.expected"));
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
        builder.error(XPathBundle.message("parsing.error.expression.expected"));
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
        builder.error(XPathBundle.message("parsing.error.expression.expected"));
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
        builder.error(XPathBundle.message("parsing.error.expression.expected"));
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
      checkMatches(builder, XPath2TokenTypes.OF, XPathBundle.message("parsing.error.of.expected"));
      if (!parseSequenceType(builder)) {
        builder.error(XPathBundle.message("parsing.error.sequence.type.expected"));
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

      checkMatches(builder, XPath2TokenTypes.AS, XPathBundle.message("parsing.error.as.expected"));
      if (!parseSequenceType(builder)) {
        builder.error(XPathBundle.message("parsing.error.sequence.type.expected"));
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

      checkMatches(builder, XPath2TokenTypes.AS, XPathBundle.message("parsing.error.as.expected"));
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

      checkMatches(builder, XPath2TokenTypes.AS, XPathBundle.message("parsing.error.as.expected"));
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
      builder.error(XPathBundle.message("parsing.error.qname.expected"));
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

  private @Nullable IElementType parseItemOrEmptySequenceType(PsiBuilder builder) {
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
        checkMatches(builder, XPathTokenTypes.NCNAME, XPathBundle.message("parsing.error.ncname.expected"));
      }
      return true;
    }
    return false;
  }

  @Override
  protected boolean parseUnaryExpression(PsiBuilder builder) {
    if (XPathTokenTypes.ADD_OPS.contains(builder.getTokenType())) {
      PsiBuilder.Marker expr = builder.mark();
      do {
        builder.advanceLexer();

        if (!parseUnaryExpression(builder)) {
          builder.error(XPathBundle.message("parsing.error.expression.expected"));
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
        builder.error(XPathBundle.message("parsing.error.relative.path.expected"));
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
        builder.error(XPathBundle.message("parsing.error.expression.expected"));
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
      checkMatches(builder, XPathTokenTypes.COLCOL, XPathBundle.message("parsing.error.double.colon.expected"));
      if (!parseNodeTest(builder)) {
        builder.error(XPathBundle.message("parsing.error.node.test.expected"));
      }
    } else if (builder.getTokenType() == XPathTokenTypes.AT) {
      builder.advanceLexer();
      mark.done(XPathElementTypes.AXIS_SPECIFIER);
      if (!parseNodeTest(builder)) {
        builder.error(XPathBundle.message("parsing.error.node.test.expected"));
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
        builder.error(XPathBundle.message("parsing.error.ncname.expected"));
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
