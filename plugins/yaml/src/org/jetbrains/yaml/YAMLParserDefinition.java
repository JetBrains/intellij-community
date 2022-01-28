// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.lexer.YAMLFlexLexer;
import org.jetbrains.yaml.parser.YAMLParser;
import org.jetbrains.yaml.psi.impl.*;

public class YAMLParserDefinition implements ParserDefinition, YAMLElementTypes {
  private static final TokenSet myCommentTokens = TokenSet.create(YAMLTokenTypes.COMMENT);

  @Override
  @NotNull
  public Lexer createLexer(final Project project) {
    return new YAMLFlexLexer();
  }

  @Override
  public @NotNull PsiParser createParser(final Project project) {
    return new YAMLParser();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE;
  }

  @Override
  @NotNull
  public TokenSet getWhitespaceTokens() {
    return TokenSet.create(YAMLTokenTypes.WHITESPACE);
  }

  @Override
  @NotNull
  public TokenSet getCommentTokens() {
    return myCommentTokens;
  }

  @Override
  @NotNull
  public TokenSet getStringLiteralElements() {
    return YAMLElementTypes.TEXT_SCALAR_ITEMS;
  }

  @Override
  @NotNull
  public PsiElement createElement(final ASTNode node) {
    final IElementType type = node.getElementType();
    if (type == DOCUMENT){
      return new YAMLDocumentImpl(node);
    }
    if (type == KEY_VALUE_PAIR) {
      return new YAMLKeyValueImpl(node);
    }
    if (type == COMPOUND_VALUE) {
      return new YAMLCompoundValueImpl(node);
    }
    if (type == SEQUENCE) {
      return new YAMLBlockSequenceImpl(node);
    }
    if (type == MAPPING) {
      return new YAMLBlockMappingImpl(node);
    }
    if (type == SEQUENCE_ITEM) {
      return new YAMLSequenceItemImpl(node);
    }
    if (type == HASH) {
      return new YAMLHashImpl(node);
    }
    if (type == ARRAY) {
      return new YAMLArrayImpl(node);
    }
    if (type == SCALAR_LIST_VALUE) {
      return new YAMLScalarListImpl(node);
    }
    if (type == SCALAR_TEXT_VALUE) {
      return new YAMLScalarTextImpl(node);
    }
    if (type == SCALAR_PLAIN_VALUE) {
      return new YAMLPlainTextImpl(node);
    }
    if (type == SCALAR_QUOTED_STRING) {
      return new YAMLQuotedTextImpl(node);
    }
    if (type == ANCHOR_NODE) {
      return new YAMLAnchorImpl(node);
    }
    if (type == ALIAS_NODE) {
      return new YAMLAliasImpl(node);
    }
    return new YAMLPsiElementImpl(node);
  }

  @Override
  public @NotNull PsiFile createFile(final @NotNull FileViewProvider viewProvider) {
    return new YAMLFileImpl(viewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(final ASTNode left, final ASTNode right) {
    return SpaceRequirements.MAY;
  }
}
