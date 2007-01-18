package com.intellij.psi.impl.source.tree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.jsp.JspElementType;

public class XmlFileElement extends FileElement{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.XmlFileElement");

  public XmlFileElement() {
    super(XML_FILE);
  }

  public XmlFileElement(IElementType type) {
    super(type);
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == XML_DOCUMENT ||
        child.getElementType() == JspElementType.JSP_DOCUMENT ||
        child.getElementType() == HTML_DOCUMENT) {
      return ChildRole.XML_DOCUMENT;
    }
    else {
      return ChildRole.NONE;
    }
  }
}
