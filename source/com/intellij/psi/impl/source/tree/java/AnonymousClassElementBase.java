/*
 * Created by IntelliJ IDEA.
 * User: ven
 * Date: Jun 10, 2004
 * Time: 8:05:20 PM
 */
package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.*;

public abstract class AnonymousClassElementBase extends ClassElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.AnonymousClassElement");

  public AnonymousClassElementBase(IElementType type) {
    super(type);
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.BASE_CLASS_REFERENCE:
        return firstChild.getElementType() == JavaElementType.JAVA_CODE_REFERENCE ? firstChild : null;

      case ChildRole.ARGUMENT_LIST:
        return TreeUtil.findChild(this, JavaElementType.EXPRESSION_LIST);

      case ChildRole.LBRACE:
        return TreeUtil.findChild(this, JavaTokenType.LBRACE);

      case ChildRole.RBRACE:
        return TreeUtil.findChildBackward(this, JavaTokenType.RBRACE);
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaElementType.JAVA_CODE_REFERENCE) {
      return getChildRole(child, ChildRole.BASE_CLASS_REFERENCE);
    }
    else if (i == JavaElementType.EXPRESSION_LIST) {
      return ChildRole.ARGUMENT_LIST;
    }
    else if (i == JavaElementType.FIELD) {
      return ChildRole.FIELD;
    }
    else if (i == JavaElementType.METHOD) {
      return ChildRole.METHOD;
    }
    else if (i == JavaElementType.CLASS_INITIALIZER) {
      return ChildRole.CLASS_INITIALIZER;
    }
    else if (i == JavaTokenType.LBRACE) {
      return getChildRole(child, ChildRole.LBRACE);
    }
    else if (i == JavaTokenType.RBRACE) {
      return getChildRole(child, ChildRole.RBRACE);
    }
    else {
      return ChildRole.NONE;
    }
  }
}