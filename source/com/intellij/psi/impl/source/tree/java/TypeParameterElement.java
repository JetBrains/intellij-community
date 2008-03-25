package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.lang.ASTNode;

/**
 * @author max
 */
public class TypeParameterElement extends RepositoryTreeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.TypeParameterElement");

  public TypeParameterElement() {
    super(ElementType.TYPE_PARAMETER);
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    final IElementType i = child.getElementType();
    if (i == JavaTokenType.IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else if (i == JavaElementType.EXTENDS_BOUND_LIST) {
      return getChildRole(child, ChildRole.EXTENDS_LIST);
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));

    switch (role) {
      default:
        return null;

      case ChildRole.NAME:
        return TreeUtil.findChild(this, JavaTokenType.IDENTIFIER);

      case ChildRole.EXTENDS_LIST:
        return TreeUtil.findChild(this, JavaElementType.EXTENDS_BOUND_LIST);
    }
  }
}
