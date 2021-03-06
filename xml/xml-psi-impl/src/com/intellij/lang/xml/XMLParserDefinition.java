// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.XmlLexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.parsing.xml.XmlParser;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.impl.source.xml.stub.XmlStubBasedElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class XMLParserDefinition implements ParserDefinition {
  @Override
  @NotNull
  public Lexer createLexer(Project project) {
    return new XmlLexer();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return XmlElementType.XML_FILE;
  }

  @Override
  @NotNull
  public TokenSet getWhitespaceTokens() {
    return XmlTokenType.WHITESPACES;
  }

  @Override
  @NotNull
  public TokenSet getCommentTokens() {
    return XmlTokenType.COMMENTS;
  }

  @Override
  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @Override
  @NotNull
  public PsiParser createParser(final Project project) {
    return new XmlParser();
  }

  @Override
  @NotNull
  public PsiElement createElement(ASTNode node) {
    if (node.getElementType() instanceof XmlStubBasedElementType) {
      //noinspection rawtypes
      return ((XmlStubBasedElementType)node.getElementType()).createPsi(node);
    }
    return PsiUtilCore.NULL_PSI_ELEMENT;
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new XmlFileImpl(viewProvider, XmlElementType.XML_FILE);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return canStickTokensTogether(left, right);
  }

  /**
   * @deprecated use {@link XMLParserDefinition#canStickTokensTogether(ASTNode, ASTNode)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static SpaceRequirements canStickTokensTogetherByLexerInXml(final ASTNode left,
                                                                                      final ASTNode right,
                                                                                      final Lexer lexer,
                                                                                      int state) {
    return canStickTokensTogether(left, right);
  }

  public static SpaceRequirements canStickTokensTogether(final ASTNode left, final ASTNode right) {
    if (left.getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN ||
        right.getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
      return SpaceRequirements.MUST_NOT;
    }
    if (left.getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER && right.getElementType() == XmlTokenType.XML_NAME) {
      return SpaceRequirements.MUST;
    }
    if (left.getElementType() == XmlTokenType.XML_NAME && right.getElementType() == XmlTokenType.XML_NAME) {
      return SpaceRequirements.MUST;
    }
    if (left.getElementType() == XmlTokenType.XML_TAG_NAME && right.getElementType() == XmlTokenType.XML_NAME) {
      return SpaceRequirements.MUST;
    }
    return SpaceRequirements.MAY;
  }
}
