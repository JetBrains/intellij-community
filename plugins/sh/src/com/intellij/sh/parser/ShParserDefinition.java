// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.parser;

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
import com.intellij.sh.ShTypes;
import com.intellij.sh.lexer.ShLexer;
import com.intellij.sh.lexer.ShTokenTypes;
import com.intellij.sh.psi.ShFile;
import com.intellij.sh.psi.ShFileElementType;
import org.jetbrains.annotations.NotNull;

public class ShParserDefinition implements ParserDefinition, ShTokenTypes {
  @NotNull
  @Override
  public Lexer createLexer(Project project) {
    return new ShLexer();
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new ShParser();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return ShFileElementType.INSTANCE;
  }

  @NotNull
  @Override
  public TokenSet getWhitespaceTokens() {
    return whitespaceTokens;
  }

  @NotNull
  @Override
  public TokenSet getCommentTokens() {
    return commentTokens;
  }

  @NotNull
  @Override
  public TokenSet getStringLiteralElements() {
    return stringLiterals;
  }

  @NotNull
  @Override
  public PsiElement createElement(ASTNode astNode) {
    return ShTypes.Factory.createElement(astNode);
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider fileViewProvider) {
    return new ShFile(fileViewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }
}