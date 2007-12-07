package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiAssertStatementImpl extends CompositePsiElement implements PsiAssertStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiAssertStatementImpl");

  public PsiAssertStatementImpl() {
    super(ASSERT_STATEMENT);
  }

  public PsiExpression getAssertCondition() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.CONDITION);
  }

  public PsiExpression getAssertDescription() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.ASSERT_DESCRIPTION);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.ASSERT_KEYWORD:
        return TreeUtil.findChild(this, ASSERT_KEYWORD);

      case ChildRole.CONDITION:
        return TreeUtil.findChild(this, EXPRESSION_BIT_SET);

      case ChildRole.COLON:
        return TreeUtil.findChild(this, COLON);

      case ChildRole.ASSERT_DESCRIPTION:
        {
          ASTNode colon = findChildByRole(ChildRole.COLON);
          if (colon == null) return null;
          ASTNode child;
          for(child = colon.getTreeNext(); child != null; child = child.getTreeNext()){
            if (EXPRESSION_BIT_SET.contains(child.getElementType())) break;
          }
          return child;
        }

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChild(this, SEMICOLON);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == ASSERT_KEYWORD) {
      return ChildRole.ASSERT_KEYWORD;
    }
    else if (i == COLON) {
      return ChildRole.COLON;
    }
    else if (i == SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        int role = getChildRole(child, ChildRole.CONDITION);
        if (role != ChildRole.NONE) return role;
        return ChildRole.ASSERT_DESCRIPTION;
      }
      return ChildRole.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAssertStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiAssertStatement";
  }
}
