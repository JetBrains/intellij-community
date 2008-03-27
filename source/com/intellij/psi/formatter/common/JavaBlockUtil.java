/*
 * @author max
 */
package com.intellij.psi.formatter.common;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;

public class JavaBlockUtil {
  public static boolean mayShiftIndentInside(final ASTNode leaf) {
    return (isComment(leaf) && !checkJspTexts(leaf))
           || leaf.getElementType() == TokenType.WHITE_SPACE
           || leaf.getElementType() == XmlElementType.XML_DATA_CHARACTERS
           || leaf.getElementType() == JspElementType.JAVA_CODE
           || leaf.getElementType() == JspElementType.JSP_SCRIPTLET
           || leaf.getElementType() == XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN;
  }

  private static boolean checkJspTexts(final ASTNode leaf) {
    ASTNode child = leaf.getFirstChildNode();
    while(child != null){
      if(child instanceof OuterLanguageElement) return true;
      child = child.getTreeNext();
    }
    return false;
  }

  private static  boolean isComment(final ASTNode node) {
    final PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(node);
    if (psiElement instanceof PsiComment) return true;
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(psiElement.getLanguage());
    if (parserDefinition == null) return false;
    final TokenSet commentTokens = parserDefinition.getCommentTokens();
    return commentTokens.contains(node.getElementType());
  }
}