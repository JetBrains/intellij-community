// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.commandInterface.commandLine;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.commandInterface.commandLine.CommandLineElementTypes.Factory;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineFile;
import org.jetbrains.annotations.NotNull;

/**
 * Command line language parser definition
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineParserDefinition implements ParserDefinition {
  public static final IFileElementType FILE_TYPE = new IFileElementType(CommandLineLanguage.INSTANCE);

  @NotNull
  @Override
  public Lexer createLexer(final Project project) {
    return new FlexAdapter(new _CommandLineLexer());
  }

  @Override
  public @NotNull PsiParser createParser(final Project project) {
    return new CommandLineParser();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE_TYPE;
  }


  @NotNull
  @Override
  public TokenSet getWhitespaceTokens() {
    return TokenSet.create(TokenType.WHITE_SPACE);
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
  public PsiElement createElement(final ASTNode node) {
    return Factory.createElement(node);
  }

  @Override
  public @NotNull PsiFile createFile(final @NotNull FileViewProvider viewProvider) {
    return new CommandLineFile(viewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(final ASTNode left, final ASTNode right) {
    return SpaceRequirements.MAY;
  }
}
