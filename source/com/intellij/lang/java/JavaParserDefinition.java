package com.intellij.lang.java;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2005
 * Time: 12:40:22 PM
 * To change this template use File | Settings | File Templates.
 */
public class JavaParserDefinition implements ParserDefinition {
  private Project myProject;

  public JavaParserDefinition(final Project project) {
    myProject = project;
  }

  public Lexer createLexer() {
    return new JavaLexer(PsiManager.getInstance(myProject).getEffectiveLanguageLevel());
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
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    if (fileIndex.isInSource(file)) {
      return new PsiJavaFileImpl(myProject, file);
    }
    else {
      return new PsiPlainTextFileImpl(myProject, file);
    }
  }

  public PsiFile createFile(String name, CharSequence text) {
    return new PsiJavaFileImpl(myProject, name, text);
  }
}
