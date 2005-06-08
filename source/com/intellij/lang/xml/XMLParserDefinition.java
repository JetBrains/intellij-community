package com.intellij.lang.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.psi.xml.XmlElementType;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2005
 * Time: 1:00:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class XMLParserDefinition implements ParserDefinition {
  public Lexer createLexer(Project project) {
    return null;
  }

  public IFileElementType getFileNodeType() {
    return XmlElementType.XML_FILE;
  }

  public TokenSet getWhitespaceTokens() {
    return XmlTokenType.WHITESPACES;
  }

  public TokenSet getCommentTokens() {
    return XmlTokenType.COMMENTS;
  }

  public PsiParser createParser(final Project project) {
    return null;
  }

  public PsiElement createElement(ASTNode node) {
    return null;
  }

  public PsiFile createFile(final Project project, VirtualFile file) {
    return new XmlFileImpl(project, file);
  }

  public PsiFile createFile(final Project project, String name, CharSequence text) {
    return new XmlFileImpl(project, name, text, StdFileTypes.XML);
  }
}
