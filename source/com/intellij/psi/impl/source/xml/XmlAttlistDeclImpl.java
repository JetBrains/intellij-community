package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.xml.XmlAttlistDecl;
import com.intellij.psi.xml.XmlAttributeDecl;
import com.intellij.psi.xml.XmlElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike
 */
public class XmlAttlistDeclImpl extends XmlElementImpl implements XmlAttlistDecl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlAttlistDeclImpl");

  public XmlAttlistDeclImpl() {
    super(XML_ATTLIST_DECL);
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == XML_NAME) {
      return ChildRole.XML_NAME;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public XmlElement getNameElement() {
    return (XmlElement)findChildByRoleAsPsiElement(ChildRole.XML_NAME);
  }

  public XmlAttributeDecl[] getAttributeDecls() {
    final List result = new ArrayList();
    processElements(new FilterElementProcessor(new ClassFilter(XmlAttributeDecl.class), result), this);
    return (XmlAttributeDecl[])result.toArray(new XmlAttributeDecl[result.size()]);
  }
}
