// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config;

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
import com.jetbrains.python.buildout.config.lexer.BuildoutCfgFlexLexer;
import com.jetbrains.python.buildout.config.psi.BuildoutCfgASTFactory;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgFile;
import org.jetbrains.annotations.NotNull;

public class BuildoutCfgParserDefinition implements ParserDefinition, BuildoutCfgElementTypes, BuildoutCfgTokenTypes {
  private final BuildoutCfgASTFactory astFactory = new BuildoutCfgASTFactory();

  @Override
  @NotNull
  public Lexer createLexer(final Project project) {
    return new BuildoutCfgFlexLexer();
  }

  @Override
  public @NotNull PsiParser createParser(final Project project) {
    return new BuildoutCfgParser();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE;
  }

  @Override
  @NotNull
  public TokenSet getWhitespaceTokens() {
    return TokenSet.create(WHITESPACE);
  }

  @Override
  @NotNull
  public TokenSet getCommentTokens() {
    return TokenSet.create(COMMENT);
  }

  @Override
  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSet.create(TEXT);
  }

  @Override
  @NotNull
  public PsiElement createElement(final ASTNode node) {
    return astFactory.create(node);
  }

  @Override
  public @NotNull PsiFile createFile(final @NotNull FileViewProvider viewProvider) {
    return new BuildoutCfgFile(viewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(final ASTNode left, final ASTNode right) {
    return SpaceRequirements.MAY;
  }
}
