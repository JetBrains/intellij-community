package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.*;

public class ParameterElement extends RepositoryTreeElement{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ParameterElement");

  public ParameterElement() {
    super(PARAMETER);
  }

  protected ParameterElement(IElementType type) {
    super(type);
  }

  public int getTextOffset() {
    return findChildByRole(ChildRole.NAME).getTextOffset();
  }

  public TreeElement findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.MODIFIER_LIST:
        return TreeUtil.findChild(this, JavaElementType.MODIFIER_LIST);

      case ChildRole.NAME:
        return TreeUtil.findChild(this, JavaTokenType.IDENTIFIER);

      case ChildRole.TYPE:
        return TreeUtil.findChild(this, JavaElementType.TYPE);

    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaElementType.MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else if (i == JavaElementType.TYPE) {
      return getChildRole(child, ChildRole.TYPE);
    }
    else if (i == JavaTokenType.IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else {
      return ChildRole.NONE;
    }
  }
}

