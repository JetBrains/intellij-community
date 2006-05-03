package com.intellij.lang.java;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtil;
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
    throw new UnsupportedOperationException("Cannot create lexer");
  }

  @NotNull
  public static Lexer createLexer(LanguageLevel languageLevel) {
    return new JavaLexer(languageLevel);
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

  public PsiFile createFile(FileViewProvider viewProvider) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(viewProvider.getManager().getProject()).getFileIndex();
    if (!viewProvider.isPhysical() || fileIndex.isInSource(viewProvider.getVirtualFile())) {
      return new PsiJavaFileImpl(viewProvider);
    }
    else {
      return new PsiPlainTextFileImpl(viewProvider);
    }
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    final PsiFile containingFile = left.getTreeParent().getPsi().getContainingFile();
    final Lexer lexer;
    if(containingFile instanceof PsiJavaFile)
      lexer = new JavaLexer(((PsiJavaFile)containingFile).getLanguageLevel());
    else lexer = new JavaLexer(LanguageLevel.HIGHEST);
    if(right.getElementType() == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) return SpaceRequirements.MUST_NOT;
    final SpaceRequirements spaceRequirements = LanguageUtil.canStickTokensTogetherByLexer(left, right, lexer, 0);
    if(left.getElementType() == JavaTokenType.END_OF_LINE_COMMENT) return SpaceRequirements.MUST_LINE_BREAK;

    if(left.getElementType() == JavaDocTokenType.DOC_COMMENT_DATA) {
      String text = left.getText();
      if (text.length() > 0 && Character.isWhitespace(text.charAt(text.length() - 1))) {
        return SpaceRequirements.MAY;
      }
    }

    if(right.getElementType() == JavaDocTokenType.DOC_COMMENT_DATA) {
      String text = right.getText();
      if (text.length() > 0 && Character.isWhitespace(text.charAt(0))) {
        return SpaceRequirements.MAY;
      }
    }

    return spaceRequirements;
  }
}
