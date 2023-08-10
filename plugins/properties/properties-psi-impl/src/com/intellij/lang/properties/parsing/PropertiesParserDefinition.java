// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.parsing;

import com.intellij.lang.*;
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
import com.intellij.psi.tree.ILightStubFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;

public class PropertiesParserDefinition implements ParserDefinition {

  public static final ILightStubFileElementType FILE_ELEMENT_TYPE = new ILightStubFileElementType("properties", PropertiesLanguage.INSTANCE) {
    @Override
    public FlyweightCapableTreeStructure<LighterASTNode> parseContentsLight(ASTNode chameleon) {
      PsiElement psi = chameleon.getPsi();
      assert psi != null : "Bad chameleon: " + chameleon;

      Project project = psi.getProject();
      PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
      PsiBuilder builder = factory.createBuilder(project, chameleon);
      ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(getLanguage());
      assert parserDefinition != null : this;
      PropertiesParser parser = new PropertiesParser();
      return parser.parseLight(this, builder);
    }
  };

  @Override
  @NotNull
  public Lexer createLexer(Project project) {
    return new PropertiesLexer();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE_ELEMENT_TYPE;
  }

  @Override
  @NotNull
  public TokenSet getWhitespaceTokens() {
    return PropertiesTokenTypes.WHITESPACES;
  }

  @Override
  @NotNull
  public TokenSet getCommentTokens() {
    return PropertiesTokenTypes.COMMENTS;
  }

  @Override
  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @Override
  @NotNull
  public PsiParser createParser(final Project project) {
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
