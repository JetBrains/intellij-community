/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class XMLParserDefinition implements ParserDefinition {
  @NotNull
  public Lexer createLexer(Project project) {
    return new XmlLexer();
  }

  public IFileElementType getFileNodeType() {
    return XmlElementType.XML_FILE;
  }

  @NotNull
  public TokenSet getWhitespaceTokens() {
    return XmlTokenType.WHITESPACES;
  }

  @NotNull
  public TokenSet getCommentTokens() {
    return XmlTokenType.COMMENTS;
  }

  @NotNull
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @NotNull
  public PsiParser createParser(final Project project) {
    return new XmlParser();
  }

  @NotNull
  public PsiElement createElement(ASTNode node) {
    return PsiUtilBase.NULL_PSI_ELEMENT;
  }

  public PsiFile createFile(FileViewProvider viewProvider) {
    return new XmlFileImpl(viewProvider, XmlElementType.XML_FILE);
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    final Lexer lexer = createLexer(left.getPsi().getProject());
    return XmlUtil.canStickTokensTogetherByLexerInXml(left, right, lexer, 0);
  }

}
