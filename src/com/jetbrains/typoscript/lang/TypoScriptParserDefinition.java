package com.jetbrains.typoscript.lang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.typoscript.lang.psi.TypoScriptFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author lene
 *         Date: 11.04.12
 */
public class TypoScriptParserDefinition implements ParserDefinition {
  @NotNull
  @Override
  public Lexer createLexer(Project project) {
    return new TypoScriptLexer();
  }

  @Override
  public PsiParser createParser(Project project) {
    return new TypoScriptGeneratedParser();
  }

  @Override
  public IFileElementType getFileNodeType() {
    return new IStubFileElementType(TypoScriptLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public TokenSet getWhitespaceTokens() {
    return TypoScriptTokenTypes.WHITESPACES;
  }

  @NotNull
  @Override
  public TokenSet getCommentTokens() {
    return TypoScriptTokenTypes.COMMENTS;
  }

  @NotNull
  @Override
  public TokenSet getStringLiteralElements() {
    return TypoScriptTokenTypes.STRING_LITERALS;
  }

  @NotNull
  @Override
  public PsiElement createElement(ASTNode node) {
    return TypoScriptElementTypes.Factory.createElement(node);
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new TypoScriptFile(viewProvider);
  }

  @Override
  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }
}
