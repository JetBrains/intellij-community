package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementContentSpec;
import com.intellij.psi.xml.XmlNotationDecl;
import com.intellij.psi.tree.IElementType;

/**
 * @author Mike
 */
public class XmlNotationDeclImpl extends XmlElementImpl implements XmlNotationDecl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlElementDeclImpl");

  public XmlNotationDeclImpl() {
    super(XML_NOTATION_DECL);
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == XML_ELEMENT_CONTENT_SPEC) {
      return ChildRole.XML_ELEMENT_CONTENT_SPEC;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public XmlElement getNameElement() {
    return (XmlElement)findChildByRoleAsPsiElement(ChildRole.XML_NAME);
  }

  public XmlElementContentSpec getContentSpecElement() {
    return (XmlElementContentSpec)findChildByRoleAsPsiElement(ChildRole.XML_ELEMENT_CONTENT_SPEC);
  }
}
