package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.util.IncorrectOperationException;

public class PsiIfStatementImpl extends CompositePsiElement implements PsiIfStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiIfStatementImpl");

  public PsiIfStatementImpl() {
    super(IF_STATEMENT);
  }

  public PsiExpression getCondition() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.CONDITION);
  }

  public void deleteChildInternal(TreeElement child) {
    if (child == getElseBranch()) {
      TreeElement elseKeyword = findChildByRole(ChildRole.ELSE_KEYWORD);
      if (elseKeyword != null) {
        super.deleteChildInternal(elseKeyword);
      }
    }
    super.deleteChildInternal(child);
  }

  public PsiStatement getThenBranch() {
    return (PsiStatement)findChildByRoleAsPsiElement(ChildRole.THEN_BRANCH);
  }

  public PsiStatement getElseBranch() {
    return (PsiStatement)findChildByRoleAsPsiElement(ChildRole.ELSE_BRANCH);
  }

  public PsiJavaToken getLParenth() {
    return (PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.LPARENTH);
  }

  public PsiJavaToken getRParenth() {
    return (PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.RPARENTH);
  }

  public PsiKeyword getElseElement() {
    return (PsiKeyword)findChildByRoleAsPsiElement(ChildRole.ELSE_KEYWORD);
  }

  public void setElseBranch(PsiStatement statement) throws IncorrectOperationException {
    PsiStatement elseBranch = getElseBranch();
    if (elseBranch != null) elseBranch.delete();
    PsiKeyword elseElement = getElseElement();
    if (elseElement != null) elseElement.delete();

    PsiElementFactory elementFactory = getManager().getElementFactory();
    PsiIfStatement ifStatement = (PsiIfStatement)elementFactory.createStatementFromText("if (true) {} else {}", null);
    ifStatement.getElseBranch().replace(statement);

    addRange(ifStatement.getElseElement(), ifStatement.getLastChild());
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.IF_KEYWORD:
        return TreeUtil.findChild(this, IF_KEYWORD);

      case ChildRole.LPARENTH:
        return TreeUtil.findChild(this, LPARENTH);

      case ChildRole.CONDITION:
        return TreeUtil.findChild(this, EXPRESSION_BIT_SET);

      case ChildRole.RPARENTH:
        return TreeUtil.findChild(this, RPARENTH);

      case ChildRole.THEN_BRANCH:
        return TreeUtil.findChild(this, STATEMENT_BIT_SET);

      case ChildRole.ELSE_KEYWORD:
        return TreeUtil.findChild(this, ELSE_KEYWORD);

      case ChildRole.ELSE_BRANCH:
        {
          TreeElement elseKeyword = findChildByRole(ChildRole.ELSE_KEYWORD);
          if (elseKeyword == null) return null;
          for(TreeElement child = elseKeyword.getTreeNext(); child != null; child = child.getTreeNext()){
            if (STATEMENT_BIT_SET.isInSet(child.getElementType())) return child;
          }
          return null;
        }
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == IF_KEYWORD) {
      return ChildRole.IF_KEYWORD;
    }
    else if (i == ELSE_KEYWORD) {
      return ChildRole.ELSE_KEYWORD;
    }
    else if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else {
      if (ElementType.EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.CONDITION;
      }
      else if (ElementType.STATEMENT_BIT_SET.isInSet(child.getElementType())) {
        if (findChildByRoleAsPsiElement(ChildRole.THEN_BRANCH) == child) {
          return ChildRole.THEN_BRANCH;
        }
        else {
          return ChildRole.ELSE_BRANCH;
        }
      }
      else {
        return ChildRole.NONE;
      }
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitIfStatement(this);
  }

  public String toString() {
    return "PsiIfStatement";
  }
}
