package com.intellij.psi.impl.source.tree;

import com.intellij.openapi.diagnostic.Logger;

public class XmlFileElement extends FileElement{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.XmlFileElement");

  public XmlFileElement() {
    super(XML_FILE);
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == XML_DOCUMENT) {
      return ChildRole.XML_DOCUMENT;
    }
    else {
      return ChildRole.NONE;
    }
  }
}
