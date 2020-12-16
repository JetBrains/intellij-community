// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.jira.jql;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
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
  public @NotNull PsiParser createParser(Project project) {
    return new JqlParser();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
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
    return JqlElementTypes.Factory.createElement(node);
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new JqlFile(viewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }
}
