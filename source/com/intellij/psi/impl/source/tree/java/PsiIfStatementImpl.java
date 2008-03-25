package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiIfStatementImpl extends CompositePsiElement implements PsiIfStatement, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiIfStatementImpl");

  public PsiIfStatementImpl() {
    super(IF_STATEMENT);
  }

  public PsiExpression getCondition() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.CONDITION);
  }

  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child == getElseBranch()) {
      ASTNode elseKeyword = findChildByRole(ChildRole.ELSE_KEYWORD);
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

    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(getManager().getProject()).getElementFactory();
    PsiIfStatement ifStatement = (PsiIfStatement)elementFactory.createStatementFromText("if (true) {} else {}", null);
    ifStatement.getElseBranch().replace(statement);

    addRange(ifStatement.getElseElement(), ifStatement.getLastChild());
  }
  public void setThenBranch(PsiStatement statement) throws IncorrectOperationException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(getManager().getProject()).getElementFactory();
    ASTNode keyword = findChildByRole(ChildRole.IF_KEYWORD);
    LOG.assertTrue(keyword != null);
    PsiIfStatement ifStatementPattern = (PsiIfStatement)elementFactory.createStatementFromText("if (){}", this);
    if (getLParenth() == null) {
      addAfter(ifStatementPattern.getLParenth(), keyword.getPsi());
    }
    if (getRParenth() == null) {
      PsiElement anchor = getCondition() == null ? getLParenth() : getCondition();
      addAfter(ifStatementPattern.getRParenth(), anchor);
    }
    PsiStatement thenBranch = getThenBranch();
    if (thenBranch == null) {
      addAfter(statement, getRParenth());
    }
    else {
      thenBranch.replace(statement);
    }
  }

  public ASTNode findChildByRole(int role) {
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
          ASTNode elseKeyword = findChildByRole(ChildRole.ELSE_KEYWORD);
          if (elseKeyword == null) return null;
          for(ASTNode child = elseKeyword.getTreeNext(); child != null; child = child.getTreeNext()){
            if (STATEMENT_BIT_SET.contains(child.getElementType())) return child;
          }
          return null;
        }
    }
  }

  public int getChildRole(ASTNode child) {
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
      if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.CONDITION;
      }
      else if (ElementType.STATEMENT_BIT_SET.contains(child.getElementType())) {
        if (findChildByRoleAsPsiElement(ChildRole.THEN_BRANCH) == child) {
          return ChildRole.THEN_BRANCH;
        }
        else {
          return ChildRole.ELSE_BRANCH;
        }
      }
      else {
        return ChildRoleBase.NONE;
      }
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitIfStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiIfStatement";
  }
}
