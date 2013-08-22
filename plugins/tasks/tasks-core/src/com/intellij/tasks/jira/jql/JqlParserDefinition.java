package com.intellij.tasks.jira.jql;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.tasks.jira.jql.psi.impl.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlParserDefinition implements ParserDefinition {
  private static final Logger LOG = Logger.getInstance(JqlParserDefinition.class);

  @NotNull
  @Override
  public Lexer createLexer(Project project) {
    return new JqlLexer();
  }

  @Override
  public PsiParser createParser(Project project) {
    return new JqlParser();
  }

  @Override
  public IFileElementType getFileNodeType() {
    return JqlElementTypes.FILE;
  }

  @NotNull
  @Override
  public TokenSet getWhitespaceTokens() {
    return JqlTokenTypes.WHITESPACES;
  }

  @NotNull
  @Override
  public TokenSet getCommentTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getStringLiteralElements() {
    return TokenSet.create(JqlTokenTypes.STRING_LITERAL);
  }

  @NotNull
  @Override
  public PsiElement createElement(ASTNode node) {
    IElementType type = node.getElementType();
    if (type == JqlElementTypes.QUERY) {
      return new JqlQueryImpl(node);
    }
    if (type == JqlElementTypes.AND_CLAUSE) {
      return new JqlAndClauseImpl(node);
    }
    if (type == JqlElementTypes.OR_CLAUSE) {
      return new JqlOrClauseImpl(node);
    }
    if (type == JqlElementTypes.NOT_CLAUSE) {
      return new JqlNotClauseImpl(node);
    }
    if (type == JqlElementTypes.SIMPLE_CLAUSE) {
      return new JqlSimpleClauseImpl(node);
    }
    if (type == JqlElementTypes.WAS_CLAUSE) {
      return new JqlWasClauseImpl(node);
    }
    if (type == JqlElementTypes.CHANGED_CLAUSE) {
      return new JqlChangedClauseImpl(node);
    }
    if (type == JqlElementTypes.FUNCTION_CALL) {
      return new JqlFunctionCallImpl(node);
    }
    if (type == JqlElementTypes.IDENTIFIER) {
      return new JqlIdentifierImpl(node);
    }
    if (type == JqlElementTypes.HISTORY_PREDICATE) {
      return new JqlHistoryPredicateImpl(node);
    }
    if (type == JqlElementTypes.ARGUMENT_LIST) {
      return new JqlArgumentListImpl(node);
    }
    if (type == JqlElementTypes.LITERAL) {
      return new JqlLiteralImpl(node);
    }
    if (type == JqlElementTypes.EMPTY) {
      return new JqlEmptyValueImpl(node);
    }
    if (type == JqlElementTypes.LIST) {
      return new JqlListImpl(node);
    }
    if (type == JqlElementTypes.SORT_KEY) {
      return new JqlSortKeyImpl(node);
    }
    if (type == JqlElementTypes.ORDER_BY) {
      return new JqlOrderByImpl(node);
    }
    return new ASTWrapperPsiElement(node);
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new JqlFile(viewProvider);
  }

  @Override
  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }
}
