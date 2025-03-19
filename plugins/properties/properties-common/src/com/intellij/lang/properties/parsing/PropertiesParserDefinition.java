// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lang.properties.PropertiesLanguage;
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

public class PropertiesParserDefinition implements ParserDefinition {
  public static final IFileElementType FILE_ELEMENT_TYPE = new IFileElementType("properties", PropertiesLanguage.INSTANCE);

  @Override
  public @NotNull Lexer createLexer(Project project) {
    return new PropertiesLexer();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE_ELEMENT_TYPE;
  }

  @Override
  public @NotNull TokenSet getWhitespaceTokens() {
    return PropertiesTokenTypes.WHITESPACES;
  }

  @Override
  public @NotNull TokenSet getCommentTokens() {
    return PropertiesTokenTypes.COMMENTS;
  }

  @Override
  public @NotNull TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @Override
  public @NotNull PsiParser createParser(final Project project) {
    return new PropertiesParser();
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new PropertiesFileImpl(viewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    if (left.getElementType() == PropertiesTokenTypes.END_OF_LINE_COMMENT) {
      return SpaceRequirements.MUST_LINE_BREAK;
    }
    return SpaceRequirements.MAY;
  }

  @Override
  public @NotNull PsiElement createElement(ASTNode node) {
    final IElementType type = node.getElementType();
    if (type == PropertiesElementTypes.PROPERTY_TYPE) {
      return new PropertyImpl(node);
    }
    else if (type == PropertiesElementTypes.PROPERTIES_LIST) {
      return new PropertiesListImpl(node);
    }
    throw new AssertionError("Alien element type [" + type + "]. Can't create Property PsiElement for that.");
  }
}
