package com.intellij.lang.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.XmlLexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2005
 * Time: 1:00:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class XMLParserDefinition implements ParserDefinition {
  @NotNull
  public Lexer createLexer(Project project) {
    return new XmlLexer();
  }

  public IFileElementType getFileNodeType() {
    return XmlElementType.XML_FILE;
  }

  @NotNull
  public TokenSet getWhitespaceTokens() {
    return XmlTokenType.WHITESPACES;
  }

  @NotNull
  public TokenSet getCommentTokens() {
    return XmlTokenType.COMMENTS;
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
    return new XmlFileImpl(viewProvider);
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    final Lexer lexer = createLexer(left.getPsi().getProject());
    return XmlUtil.canStickTokensTogetherByLexerInXml(left, right, lexer, 0);
  }

}
