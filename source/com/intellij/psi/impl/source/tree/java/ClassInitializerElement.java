package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;

public class ClassInitializerElement extends RepositoryTreeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ClassInitializerElement");

  public ClassInitializerElement() {
    super(CLASS_INITIALIZER);
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.MODIFIER_LIST:
        return TreeUtil.findChild(this, MODIFIER_LIST);

      case ChildRole.METHOD_BODY:
        return TreeUtil.findChild(this, CODE_BLOCK);
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == C_STYLE_COMMENT || i == END_OF_LINE_COMMENT) {
      {
        if (TreeUtil.skipElementsBack(child, WHITE_SPACE_OR_COMMENT_BIT_SET) == null) {
          return ChildRole.PRECEDING_COMMENT;
        }
        else {
          return ChildRole.NONE;
        }
      }
    }
    else if (i == MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else if (i == CODE_BLOCK) {
      return ChildRole.METHOD_BODY;
    }
    else {
      return ChildRole.NONE;
    }
  }
}
