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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2005
 * Time: 1:00:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class XMLParserDefinition implements ParserDefinition {
  private Project myProject;

  public XMLParserDefinition(final Project project) {
    myProject = project;
  }

  public Lexer createLexer() {
    return null;
  }

  public IElementType getFileNodeType() {
    return null;
  }

  public TokenSet getWhitespaceTokens() {
    return null;
  }

  public TokenSet getCommentTokens() {
    return null;
  }

  public PsiParser createParser() {
    return null;
  }

  public PsiElement createElement(ASTNode node) {
    return null;
  }

  public PsiFile createFile(VirtualFile file) {
    return new XmlFileImpl(myProject, file);
  }

  public PsiFile createFile(String name, CharSequence text) {
    return new XmlFileImpl(myProject, name, text, StdFileTypes.XML);
  }
}
