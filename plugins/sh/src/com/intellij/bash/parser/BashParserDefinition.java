package com.intellij.bash.parser;

import com.intellij.bash.BashTypes;
import com.intellij.bash.lexer.BashLexer;
import com.intellij.bash.lexer.BashTokenTypes;
import com.intellij.bash.psi.BashFile;
import com.intellij.bash.psi.BashFileElementType;
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
import org.jetbrains.annotations.NotNull;

public class BashParserDefinition implements ParserDefinition, BashTokenTypes {
  @NotNull
  @Override
  public Lexer createLexer(Project project) {
    return new BashLexer();
  }

  @Override
  public PsiParser createParser(Project project) {
    return new BashParser();
  }

  @Override
  public IFileElementType getFileNodeType() {
    return BashFileElementType.INSTANCE;
  }

  @NotNull
  @Override
  public TokenSet getWhitespaceTokens() {
    return whitespaceTokens;
  }

  @NotNull
  @Override
  public TokenSet getCommentTokens() {
    return commentTokens;
  }

  @NotNull
  @Override
  public TokenSet getStringLiteralElements() {
    return stringLiterals;
  }

  @NotNull
  @Override
  public PsiElement createElement(ASTNode astNode) {
    return BashTypes.Factory.createElement(astNode);
  }

  @Override
  public PsiFile createFile(FileViewProvider fileViewProvider) {
    return new BashFile(fileViewProvider);
  }

  @Override
  public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }
}