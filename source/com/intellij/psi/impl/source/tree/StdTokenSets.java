package com.intellij.psi.impl.source.tree;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;

/**
 * @author mike
 */
public interface StdTokenSets {
  TokenSet COMMENT_BIT_SET = TokenSet.orSet(ElementType.JAVA_COMMENT_BIT_SET, TokenSet.create(JspElementType.JSP_COMMENT, XmlElementType.XML_COMMENT));
  TokenSet WHITE_SPACE_OR_COMMENT_BIT_SET = TokenSet.orSet(ElementType.WHITE_SPACE_BIT_SET, JavaTokenType.COMMENT_BIT_SET);
}
