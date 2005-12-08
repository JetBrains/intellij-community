package com.intellij.lang.java;

import com.intellij.lang.*;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2005
 * Time: 12:40:22 PM
 * To change this template use File | Settings | File Templates.
 */
public class JavaParserDefinition implements ParserDefinition {

  @NotNull
  public Lexer createLexer(Project project) {
    return new JavaLexer(PsiManager.getInstance(project).getEffectiveLanguageLevel());
  }

  public IFileElementType getFileNodeType() {
    return JavaElementType.JAVA_FILE;
  }

  @NotNull
  public TokenSet getWhitespaceTokens() {
    return JavaTokenType.WHITESPACE_BIT_SET;
  }

  @NotNull
  public TokenSet getCommentTokens() {
    return JavaTokenType.COMMENT_BIT_SET;
  }

  @NotNull
  public PsiParser createParser(final Project project) {
    return PsiUtil.NULL_PARSER;
  }

  @NotNull
  public PsiElement createElement(ASTNode node) {
    return PsiUtil.NULL_PSI_ELEMENT;
  }

  public PsiFile createFile(final Project project, VirtualFile file) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (fileIndex.isInSource(file)) {
      return new PsiJavaFileImpl(project, file);
    }
    else {
      return new PsiPlainTextFileImpl(project, file);
    }
  }

  public PsiFile createFile(final Project project, String name, CharSequence text) {
    return new PsiJavaFileImpl(project, name, text);
  }
}
