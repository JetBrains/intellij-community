package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;

public class PsiSwitchStatementImpl extends CompositePsiElement implements PsiSwitchStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiSwitchStatementImpl");

  public PsiSwitchStatementImpl() {
    super(SWITCH_STATEMENT);
  }

  public PsiExpression getExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.SWITCH_EXPRESSION);
  }

  public PsiCodeBlock getBody() {
    return (PsiCodeBlock)findChildByRoleAsPsiElement(ChildRole.SWITCH_BODY);
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.SWITCH_KEYWORD:
        return TreeUtil.findChild(this, SWITCH_KEYWORD);

      case ChildRole.LPARENTH:
        return TreeUtil.findChild(this, LPARENTH);

      case ChildRole.SWITCH_EXPRESSION:
        return TreeUtil.findChild(this, EXPRESSION_BIT_SET);

      case ChildRole.RPARENTH:
        return TreeUtil.findChild(this, RPARENTH);

      case ChildRole.SWITCH_BODY:
        return TreeUtil.findChild(this, CODE_BLOCK);
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == SWITCH_KEYWORD) {
      return ChildRole.SWITCH_KEYWORD;
    }
    else if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else {
      if (EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.SWITCH_EXPRESSION;
      }
      else if (child.getElementType() == CODE_BLOCK) {
        return ChildRole.SWITCH_BODY;
      }
      else {
        return ChildRole.NONE;
      }
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitSwitchStatement(this);
  }

  public String toString() {
    return "PsiSwitchStatement";
  }
}
