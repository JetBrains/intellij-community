package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.xml.XmlElementContentSpec;
import com.intellij.psi.tree.IElementType;

/**
 * @author Mike
 */
public class XmlElementContentSpecImpl extends XmlElementImpl implements XmlElementContentSpec {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlElementContentSpecImpl");

  public XmlElementContentSpecImpl() {
    super(XML_ELEMENT_CONTENT_SPEC);
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == XML_CONTENT_ANY) {
      return ChildRole.XML_CONTENT_ANY;
    }
    else if (i == XML_CONTENT_EMPTY) {
      return ChildRole.XML_CONTENT_EMPTY;
    }
    else if (i == XML_PCDATA) {
      return ChildRole.XML_PCDATA;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public boolean isEmpty() {
    return findElementByTokenType(XML_CONTENT_EMPTY) != null;
  }

  public boolean isAny() {
    return findElementByTokenType(XML_CONTENT_ANY) != null;
  }

  public boolean isMixed() {
    return findElementByTokenType(XML_PCDATA) != null;
  }

  public boolean hasChildren() {
    return !(isEmpty() || isAny() || isMixed());
  }
}
