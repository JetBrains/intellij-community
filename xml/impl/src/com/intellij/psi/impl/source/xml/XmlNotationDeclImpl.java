package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.*;
import com.intellij.psi.tree.ChildRoleBase;

/**
 * @author Mike
 */
public class XmlNotationDeclImpl extends XmlElementImpl implements XmlNotationDecl, XmlElementType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlElementDeclImpl");

  public XmlNotationDeclImpl() {
    super(XML_NOTATION_DECL);
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == XML_ELEMENT_CONTENT_SPEC) {
      return XmlChildRole.XML_ELEMENT_CONTENT_SPEC;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  public XmlElement getNameElement() {
    return (XmlElement)findChildByRoleAsPsiElement(XmlChildRole.XML_NAME);
  }

  public XmlElementContentSpec getContentSpecElement() {
    return (XmlElementContentSpec)findChildByRoleAsPsiElement(XmlChildRole.XML_ELEMENT_CONTENT_SPEC);
  }
}
