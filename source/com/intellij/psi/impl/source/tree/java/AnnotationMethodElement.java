package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;


/**
 * @author ven
 */
public class AnnotationMethodElement extends MethodElement {
  public AnnotationMethodElement() {
    super(ANNOTATION_METHOD);
  }

  public TreeElement findChildByRole(int role) {
    if (role == ChildRole.ANNOTATION_DEFAULT_VALUE) {
      return TreeUtil.findChild(this, ANNOTATION_MEMBER_VALUE_BIT_SET);
    } else if (role == ChildRole.DEFAULT_KEYWORD) {
      return TreeUtil.findChild(this, DEFAULT_KEYWORD);
    }

    return super.findChildByRole(role);
  }

  public int getChildRole(TreeElement child) {
    if (child.getElementType() == DEFAULT_KEYWORD) {
      return ChildRole.DEFAULT_KEYWORD;
    } else if (ANNOTATION_MEMBER_VALUE_BIT_SET.isInSet(child.getElementType())) {
      return ChildRole.ANNOTATION_DEFAULT_VALUE;
    }

    return super.getChildRole(child);
  }
}
