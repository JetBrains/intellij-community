// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.restructuredtext.RestFile;
import com.intellij.restructuredtext.RestLanguage;
import com.intellij.restructuredtext.RestTokenTypes;
import com.intellij.restructuredtext.lexer.RestFlexLexer;
import com.intellij.restructuredtext.psi.RestASTFactory;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class RestParserDefinition implements ParserDefinition, RestTokenTypes {
  private static final IFileElementType FILE_ELEMENT_TYPE = new IFileElementType(RestLanguage.INSTANCE);

  private final RestASTFactory astFactory = new RestASTFactory();

  @Override
  public @NotNull Lexer createLexer(Project project) {
    return new RestFlexLexer();
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new RestParser();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE_ELEMENT_TYPE;
  }

  @Override
  public @NotNull TokenSet getWhitespaceTokens() {
    return TokenSet.EMPTY;
  }

  @Override
  public @NotNull TokenSet getCommentTokens() {
    return TokenSet.EMPTY;
  }

  @Override
  public @NotNull TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @Override
  public @NotNull PsiElement createElement(ASTNode node) {
    return astFactory.create(node);
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new RestFile(viewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }
}
