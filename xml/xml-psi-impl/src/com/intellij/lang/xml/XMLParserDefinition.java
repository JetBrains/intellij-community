/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class XMLParserDefinition implements ParserDefinition {
  @Override
  @NotNull
  public Lexer createLexer(Project project) {
    return new XmlLexer();
  }

  @Override
  public IFileElementType getFileNodeType() {
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
    return PsiUtilCore.NULL_PSI_ELEMENT;
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new XmlFileImpl(viewProvider, XmlElementType.XML_FILE);
  }

  @Override
  public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    final Lexer lexer = createLexer(left.getPsi().getProject());
    return canStickTokensTogetherByLexerInXml(left, right, lexer, 0);
  }

  public static SpaceRequirements canStickTokensTogetherByLexerInXml(final ASTNode left,
                                                                                      final ASTNode right,
                                                                                      final Lexer lexer,
                                                                                      int state) {
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
