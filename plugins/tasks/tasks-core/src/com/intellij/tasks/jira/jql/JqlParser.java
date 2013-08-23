package com.intellij.tasks.jira.jql;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlParser implements PsiParser {
  private static final Logger LOG = Logger.getInstance(JqlParser.class);

  @NotNull
  @Override
  public ASTNode parse(IElementType root, PsiBuilder builder) {
    //builder.setDebugMode(true);
    PsiBuilder.Marker rootMarker = builder.mark();
    // Parser should accept empty string
    if (!builder.eof()) {
      parseQuery(builder);
      while (!builder.eof()) {
        builder.error("Illegal query part");
        builder.advanceLexer();
      }
    }
    rootMarker.done(root);
    return builder.getTreeBuilt();
  }

  private boolean parseQuery(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    parseORClause(builder);
    if (builder.getTokenType() == JqlTokenTypes.ORDER_KEYWORD) {
      parseOrderBy(builder);
    }
    marker.done(JqlElementTypes.QUERY);
    return true;
  }

  private boolean parseORClause(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (!parseANDClause(builder)) {
      marker.drop();
      return false;
    }
    while (advanceIfMatches(builder, JqlTokenTypes.OR_OPERATORS)) {
      if (!parseANDClause(builder)) {
        builder.error("Expecting clause");
      }
      marker.done(JqlElementTypes.OR_CLAUSE);
      marker = marker.precede();
    }
    marker.drop();
    return true;
  }

  private boolean parseANDClause(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (!parseNOTClause(builder)) {
      marker.drop();
      return false;
    }
    while (advanceIfMatches(builder, JqlTokenTypes.AND_OPERATORS)) {
      if (!parseNOTClause(builder)) {
        builder.error("Expecting clause");
      }
      marker.done(JqlElementTypes.AND_CLAUSE);
      marker = marker.precede();
    }
    marker.drop();
    return true;
  }

  private boolean parseNOTClause(PsiBuilder builder) {
    if (JqlTokenTypes.NOT_OPERATORS.contains(builder.getTokenType())) {
      PsiBuilder.Marker marker = builder.mark();
      builder.advanceLexer();
      if (!parseNOTClause(builder)) {
        builder.error("Expecting clause");
      }
      marker.done(JqlElementTypes.NOT_CLAUSE);
      return true;
    }
    else if (builder.getTokenType() == JqlTokenTypes.LPAR) {
      return parseSubClause(builder);
    }
    return parseTerminalClause(builder);
  }

  private boolean parseSubClause(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == JqlTokenTypes.LPAR);
    PsiBuilder.Marker marker = builder.mark();
    if (!advanceIfMatches(builder, JqlTokenTypes.LPAR)) {
      marker.drop();
      return false;
    }
    parseORClause(builder);
    if (!advanceIfMatches(builder, JqlTokenTypes.RPAR)) {
      builder.error("Expecting ')'");
    }
    marker.done(JqlElementTypes.SUB_CLAUSE);
    return true;
  }

  private boolean parseTerminalClause(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (!parseFieldName(builder)) {
      marker.drop();
      return false;
    }
    if (advanceIfMatches(builder, JqlTokenTypes.IS_KEYWORD)) {
      advanceIfMatches(builder, JqlTokenTypes.NOT_KEYWORD);
      parseOperand(builder);
    }
    else if (advanceIfMatches(builder, JqlTokenTypes.SIMPLE_OPERATORS) || advanceIfMatches(builder, JqlTokenTypes.IN_KEYWORD)) {
      parseOperand(builder);
    }
    else if (advanceIfMatches(builder, JqlTokenTypes.NOT_KEYWORD)) {
      if (!advanceIfMatches(builder, JqlTokenTypes.IN_KEYWORD)) {
        builder.error("Expecting 'in'");
      }
      parseOperand(builder);
    }
    else if (builder.getTokenType() == JqlTokenTypes.WAS_KEYWORD) {
      parseWASClause(builder);
      marker.done(JqlElementTypes.WAS_CLAUSE);
      return true;
    }
    else if (builder.getTokenType() == JqlTokenTypes.CHANGED_KEYWORD) {
      parseCHANGEDClause(builder);
      marker.done(JqlElementTypes.CHANGED_CLAUSE);
      return true;
    }
    else {
      builder.error("Expecting operator");
    }
    marker.done(JqlElementTypes.SIMPLE_CLAUSE);
    return true;
  }

  private void parseCHANGEDClause(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == JqlTokenTypes.CHANGED_KEYWORD);
    if (!advanceIfMatches(builder, JqlTokenTypes.CHANGED_KEYWORD)) {
      return;
    }
    while (JqlTokenTypes.HISTORY_PREDICATES.contains(builder.getTokenType())) {
      parseHistoryPredicate(builder);
    }
  }

  private void parseWASClause(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == JqlTokenTypes.WAS_KEYWORD);
    if (!advanceIfMatches(builder, JqlTokenTypes.WAS_KEYWORD)) {
      return;
    }
    advanceIfMatches(builder, JqlTokenTypes.NOT_KEYWORD);
    advanceIfMatches(builder, JqlTokenTypes.IN_KEYWORD);
    parseOperand(builder);
    while (JqlTokenTypes.HISTORY_PREDICATES.contains(builder.getTokenType())) {
      parseHistoryPredicate(builder);
    }
  }

  private void parseHistoryPredicate(PsiBuilder builder) {
    LOG.assertTrue(JqlTokenTypes.HISTORY_PREDICATES.contains(builder.getTokenType()));
    PsiBuilder.Marker marker = builder.mark();
    if (!advanceIfMatches(builder, JqlTokenTypes.HISTORY_PREDICATES)) {
      marker.drop();
      return;
    }
    parseOperand(builder);
    marker.done(JqlElementTypes.HISTORY_PREDICATE);
  }

  private boolean parseOperand(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    boolean parsed = true;
    if (builder.getTokenType() == JqlTokenTypes.LPAR) {
      marker.drop();
      parsed = parseList(builder);
    }
    else if (advanceIfMatches(builder, JqlTokenTypes.EMPTY_VALUES)) {
      marker.done(JqlElementTypes.EMPTY);
    }
    else if (advanceIfMatches(builder, JqlTokenTypes.LITERALS)) {
      if (builder.getTokenType() == JqlTokenTypes.LPAR) {
        marker.done(JqlElementTypes.IDENTIFIER);
        marker = marker.precede();
        parseArgumentList(builder);
        marker.done(JqlElementTypes.FUNCTION_CALL);
      }
      else {
        marker.done(JqlElementTypes.LITERAL);
      }
    }
    else {
      marker.drop();
      parsed = false;
    }
    if (!parsed) {
      builder.error("Expecting either literal, list or function call");
    }
    return parsed;
  }

  private boolean parseList(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == JqlTokenTypes.LPAR);
    PsiBuilder.Marker marker = builder.mark();
    if (!advanceIfMatches(builder, JqlTokenTypes.LPAR)) {
      marker.drop();
      return false;
    }
    if (parseOperand(builder)) {
      while (advanceIfMatches(builder, JqlTokenTypes.COMMA)) {
        if (!parseOperand(builder)) {
          break;
        }
      }
    }
    if (!advanceIfMatches(builder, JqlTokenTypes.RPAR)) {
      builder.error("Expecting ')'");
    }
    marker.done(JqlElementTypes.LIST);
    return true;
  }

  private boolean parseFieldName(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (!advanceIfMatches(builder, JqlTokenTypes.VALID_FIELD_NAMES)) {
      builder.error("Expecting field name");
      marker.drop();
      return false;
    }
    marker.done(JqlElementTypes.IDENTIFIER);
    return true;
  }

  private void parseArgumentList(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == JqlTokenTypes.LPAR);
    PsiBuilder.Marker marker = builder.mark();
    if (!advanceIfMatches(builder, JqlTokenTypes.LPAR)) {
      marker.drop();
      return;
    }
    // empty argument list
    if (advanceIfMatches(builder, JqlTokenTypes.RPAR)) {
      marker.done(JqlElementTypes.ARGUMENT_LIST);
      return;
    }
    PsiBuilder.Marker argument = builder.mark();
    if (!advanceIfMatches(builder, JqlTokenTypes.VALID_ARGUMENTS)) {
      builder.error("Expecting argument");
      argument.drop();
    }
    else {
      // valid argument is always literal, at least in current implementation of JQU
      argument.done(JqlElementTypes.LITERAL);
      while (advanceIfMatches(builder, JqlTokenTypes.COMMA)) {
        argument = builder.mark();
        if (!advanceIfMatches(builder, JqlTokenTypes.VALID_ARGUMENTS)) {
          marker.drop();
          builder.error("Expecting argument");
          break;
        }
        argument.done(JqlElementTypes.LITERAL);
      }
    }
    if (!advanceIfMatches(builder, JqlTokenTypes.RPAR)) {
      builder.error("Expecting ')'");
    }
    marker.done(JqlElementTypes.ARGUMENT_LIST);
  }

  private void parseOrderBy(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (!advanceIfMatches(builder, JqlTokenTypes.ORDER_KEYWORD)) {
      marker.drop();
      return;
    }
    if (!advanceIfMatches(builder, JqlTokenTypes.BY_KEYWORD)) {
      builder.error("Expecting 'by'");
      marker.done(JqlElementTypes.ORDER_BY);
      return;
    }
    if (parseSortKey(builder)) {
      while (advanceIfMatches(builder, JqlTokenTypes.COMMA)) {
        if (!parseSortKey(builder)) {
          break;
        }
      }
    }
    marker.done(JqlElementTypes.ORDER_BY);
  }

  private boolean parseSortKey(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (!parseFieldName(builder)) {
      marker.drop();
      return false;
    }
    advanceIfMatches(builder, JqlTokenTypes.SORT_ORDERS);
    marker.done(JqlElementTypes.SORT_KEY);
    return true;
  }


  private boolean advanceIfMatches(PsiBuilder builder, IElementType type) {
    if (builder.getTokenType() == type) {
      builder.advanceLexer();
      return true;
    }
    return false;
  }

  private boolean advanceIfMatches(PsiBuilder builder, TokenSet set) {
    if (set.contains(builder.getTokenType())) {
      builder.advanceLexer();
      return true;
    }
    return false;
  }
}