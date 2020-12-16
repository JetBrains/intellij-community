// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.parsing;

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
import com.jetbrains.rest.RestFile;
import com.jetbrains.rest.RestLanguage;
import com.jetbrains.rest.RestTokenTypes;
import com.jetbrains.rest.lexer.RestFlexLexer;
import com.jetbrains.rest.psi.RestASTFactory;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class RestParserDefinition implements ParserDefinition, RestTokenTypes {
  private static final IFileElementType FILE_ELEMENT_TYPE = new IFileElementType(RestLanguage.INSTANCE);

  private final RestASTFactory astFactory = new RestASTFactory();

  @NotNull
  @Override
  public Lexer createLexer(Project project) {
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

  @NotNull
  @Override
  public TokenSet getWhitespaceTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getCommentTokens() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public PsiElement createElement(ASTNode node) {
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
