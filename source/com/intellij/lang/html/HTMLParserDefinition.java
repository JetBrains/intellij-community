package com.intellij.lang.html;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.html.HtmlFileImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2005
 * Time: 1:01:36 PM
 * To change this template use File | Settings | File Templates.
 */
public class HTMLParserDefinition implements ParserDefinition {
  public Lexer createLexer(Project project) {
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

  public PsiParser createParser(final Project project) {
    return null;
  }

  public PsiElement createElement(ASTNode node) {
    return null;
  }

  public PsiFile createFile(final Project project, VirtualFile file) {
    return new HtmlFileImpl(project, file);
  }

  public PsiFile createFile(final Project project, String name, CharSequence text) {
    return new HtmlFileImpl(project, name, text, StdFileTypes.HTML);
  }
}
