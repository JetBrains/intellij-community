package com.intellij.psi.impl.source.tree;

import com.intellij.openapi.diagnostic.Logger;

public class HtmlFileElement extends FileElement{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.HtmlFileElement");

  public HtmlFileElement() {
    super(HTML_FILE);
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == HTML_DOCUMENT) {
      return ChildRole.HTML_DOCUMENT;
    }
    else {
      return ChildRole.NONE;
    }
  }
}
