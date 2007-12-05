package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.RowIcon;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PsiClassObjectAccessExpressionImpl extends CompositePsiElement implements PsiClassObjectAccessExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiClassObjectAccessExpressionImpl");

  public PsiClassObjectAccessExpressionImpl() {
    super(CLASS_OBJECT_ACCESS_EXPRESSION);
  }

  public PsiType getType() {
    return PsiImplUtil.getType(this);
  }

  @NotNull
  public PsiTypeElement getOperand() {
    return (PsiTypeElement)findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.TYPE:
        return TreeUtil.findChild(this, TYPE);

      case ChildRole.DOT:
        return TreeUtil.findChild(this, DOT);

      case ChildRole.CLASS_KEYWORD:
        return TreeUtil.findChild(this, CLASS_KEYWORD);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == TYPE) {
      return ChildRole.TYPE;
    }
    else if (i == DOT) {
      return ChildRole.DOT;
    }
    else if (i == CLASS_KEYWORD) {
      return ChildRole.CLASS_KEYWORD;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitClassObjectAccessExpression(this);
  }

  public String toString() {
    return "PsiClassObjectAccessExpression:" + getText();
  }

  public Icon getIcon(final int flags) {
    final RowIcon rowIcon = createLayeredIcon(Icons.FIELD_ICON, 0);
    rowIcon.setIcon(Icons.PUBLIC_ICON, 1);
    return rowIcon;
  }
}

