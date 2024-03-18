// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.html;

import com.intellij.html.embedding.HtmlCustomEmbeddedContentTokenType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lang.xml.XMLParserDefinition;
import com.intellij.lexer.HtmlLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.html.HtmlEmbeddedContentImpl;
import com.intellij.psi.impl.source.html.HtmlFileImpl;
import com.intellij.psi.impl.source.xml.stub.XmlStubBasedElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.UnsupportedNodeElementTypeException;
import com.intellij.psi.xml.HtmlFileElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

public class HTMLParserDefinition implements ParserDefinition {
  public static final IFileElementType FILE_ELEMENT_TYPE = new HtmlFileElementType();

  @Override
  public @NotNull Lexer createLexer(Project project) {
    return new HtmlLexer();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return XmlElementType.HTML_FILE;
  }

  @Override
  public @NotNull TokenSet getWhitespaceTokens() {
    return XmlTokenType.WHITESPACES;
  }

  @Override
  public @NotNull TokenSet getCommentTokens() {
    return XmlTokenType.COMMENTS;
  }

  @Override
  public @NotNull TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @Override
  public @NotNull PsiParser createParser(final Project project) {
    return new HTMLParser();
  }

  @Override
  public @NotNull PsiElement createElement(ASTNode node) {
    if (node.getElementType() instanceof XmlStubBasedElementType) {
      //noinspection rawtypes
      return ((XmlStubBasedElementType)node.getElementType()).createPsi(node);
    }
    if (node.getElementType() instanceof HtmlCustomEmbeddedContentTokenType) {
      return ((HtmlCustomEmbeddedContentTokenType)node.getElementType()).createPsi(node);
    }
    if (node.getElementType() == XmlElementType.HTML_EMBEDDED_CONTENT) {
      return new HtmlEmbeddedContentImpl(node);
    }
    throw new UnsupportedNodeElementTypeException(node);
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new HtmlFileImpl(viewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return XMLParserDefinition.canStickTokensTogether(left, right);
  }
}
