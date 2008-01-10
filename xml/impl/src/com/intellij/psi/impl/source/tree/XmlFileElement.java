package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlElementType;

public class XmlFileElement extends FileElement implements XmlElementType {
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
      return XmlChildRole.XML_DOCUMENT;
    }
    else {
      return ChildRole.NONE;
    }
  }
}
