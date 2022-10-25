package com.jetbrains.performancePlugin.lang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.performancePlugin.lang.lexer.IJPerfLexerAdapter;
import com.jetbrains.performancePlugin.lang.parser.IJPerfParser;
import com.jetbrains.performancePlugin.lang.psi.IJPerfElementTypes;
import com.jetbrains.performancePlugin.lang.psi.IJPerfFile;
import org.jetbrains.annotations.NotNull;

public class IJPerfParserDefinition implements ParserDefinition {

  public static final IFileElementType FILE = new IFileElementType(IJPerfLanguage.INSTANCE);

  @NotNull
  @Override
  public Lexer createLexer(Project project) {
    return new IJPerfLexerAdapter();
  }

  @Override
  public @NotNull PsiParser createParser(Project project) {
    return new IJPerfParser();
  }

  @Override
  public @NotNull IFileElementType getFileNodeType() {
    return FILE;
  }

  @NotNull
  @Override
  public TokenSet getCommentTokens() {
    return IJPerfTokenSets.COMMENTS;
  }

  @NotNull
  @Override
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @NotNull
  @Override
  public PsiElement createElement(ASTNode node) {
    return IJPerfElementTypes.Factory.createElement(node);
  }

  @Override
  public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
    return new IJPerfFile(viewProvider);
  }

  @Override
  public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }
}
