/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.properties.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl;
import com.intellij.lang.properties.psi.impl.PropertiesListImpl;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class PropertiesParserDefinition implements ParserDefinition {
  @NotNull
  public Lexer createLexer(Project project) {
    return new PropertiesLexer();
  }

  public IFileElementType getFileNodeType() {
    return PropertiesElementTypes.FILE;
  }

  @NotNull
  public TokenSet getWhitespaceTokens() {
    return PropertiesTokenTypes.WHITESPACES;
  }

  @NotNull
  public TokenSet getCommentTokens() {
    return PropertiesTokenTypes.COMMENTS;
  }

  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @NotNull
  public PsiParser createParser(final Project project) {
    return new PropertiesParser();
  }

  public PsiFile createFile(FileViewProvider viewProvider) {
    return new PropertiesFileImpl(viewProvider);
  }

  public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }

  @NotNull
  public PsiElement createElement(ASTNode node) {
    final IElementType type = node.getElementType();
    if (type == PropertiesElementTypes.PROPERTY) {
      return new PropertyImpl(node);
    }
    else if (type == PropertiesElementTypes.PROPERTIES_LIST) {
      return new PropertiesListImpl(node);
    }
    throw new AssertionError("Alien element type [" + type + "]. Can't create Property PsiElement for that.");
  }
}
