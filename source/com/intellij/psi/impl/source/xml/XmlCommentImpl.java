package com.intellij.psi.impl.source.xml;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.xml.XmlComment;

/**
 * @author Mike
 */
public class XmlCommentImpl extends XmlElementImpl implements XmlComment {
  public XmlCommentImpl() {
    super(XML_COMMENT);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlComment(this);
  }
}
