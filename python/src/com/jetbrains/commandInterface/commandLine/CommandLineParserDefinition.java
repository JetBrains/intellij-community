/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
  public PsiParser createParser(final Project project) {
    return new CommandLineParser();
  }

  @Override
  public IFileElementType getFileNodeType() {
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
  public PsiFile createFile(final FileViewProvider viewProvider) {
    return new CommandLineFile(viewProvider);
  }

  @Override
  public SpaceRequirements spaceExistanceTypeBetweenTokens(final ASTNode left, final ASTNode right) {
    return SpaceRequirements.MAY;
  }
}
