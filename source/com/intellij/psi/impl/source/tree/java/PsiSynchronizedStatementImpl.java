package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiSynchronizedStatementImpl extends CompositePsiElement implements PsiSynchronizedStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiSynchronizedStatementImpl");

  public PsiSynchronizedStatementImpl() {
    super(SYNCHRONIZED_STATEMENT);
  }

  public PsiExpression getLockExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.LOCK);
  }

  public PsiCodeBlock getBody() {
    return (PsiCodeBlock)findChildByRoleAsPsiElement(ChildRole.BLOCK);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.SYNCHRONIZED_KEYWORD:
        return TreeUtil.findChild(this, SYNCHRONIZED_KEYWORD);

      case ChildRole.LPARENTH:
        return TreeUtil.findChild(this, LPARENTH);

      case ChildRole.LOCK:
        return TreeUtil.findChild(this, EXPRESSION_BIT_SET);

      case ChildRole.RPARENTH:
        return TreeUtil.findChild(this, RPARENTH);

      case ChildRole.BLOCK:
        return TreeUtil.findChild(this, CODE_BLOCK);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == SYNCHRONIZED_KEYWORD) {
      return ChildRole.SYNCHRONIZED_KEYWORD;
    }
    else if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else if (i == CODE_BLOCK) {
      return ChildRole.BLOCK;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.LOCK;
      }
      return ChildRole.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSynchronizedStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiSynchronizedStatement";
  }
}
