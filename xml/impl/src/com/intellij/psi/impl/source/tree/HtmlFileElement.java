package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.tree.ChildRoleBase;

public class HtmlFileElement extends FileElement implements XmlElementType  {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.HtmlFileElement");

  public HtmlFileElement() {
    super(HTML_FILE);
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == HTML_DOCUMENT) {
      return XmlChildRole.HTML_DOCUMENT;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }
}
