package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiForStatementImpl extends CompositePsiElement implements PsiForStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiForStatementImpl");

  public PsiForStatementImpl() {
    super(FOR_STATEMENT);
  }

  public PsiStatement getInitialization(){
    return (PsiStatement)findChildByRoleAsPsiElement(ChildRole.FOR_INITIALIZATION);
  }

  public PsiExpression getCondition(){
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.CONDITION);
  }

  public PsiStatement getUpdate(){
    return (PsiStatement)findChildByRoleAsPsiElement(ChildRole.FOR_UPDATE);
  }

  public PsiStatement getBody(){
    return (PsiStatement)findChildByRoleAsPsiElement(ChildRole.LOOP_BODY);
  }

  public PsiJavaToken getLParenth() {
    return (PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.LPARENTH);
  }

  public PsiJavaToken getRParenth() {
    return (PsiJavaToken) findChildByRoleAsPsiElement(ChildRole.RPARENTH);
  }

  public ASTNode findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.FOR_KEYWORD:
        return TreeUtil.findChild(this, FOR_KEYWORD);

      case ChildRole.LPARENTH:
        return TreeUtil.findChild(this, LPARENTH);

      case ChildRole.FOR_INITIALIZATION:
        final ASTNode initialization = TreeUtil.findChild(this, STATEMENT_BIT_SET);
        // should be inside parens
        ASTNode paren = findChildByRole(ChildRole.LPARENTH);
        for(ASTNode child = paren; child != null; child = child.getTreeNext()){
          if (child == initialization) return initialization;
          if (child.getElementType() == RPARENTH) return null;
        }
        return null;

      case ChildRole.CONDITION:
        return TreeUtil.findChild(this, EXPRESSION_BIT_SET);

      case ChildRole.FOR_SEMICOLON:
        return TreeUtil.findChild(this, SEMICOLON);

      case ChildRole.FOR_UPDATE:
      {
        ASTNode semicolon = findChildByRole(ChildRole.FOR_SEMICOLON);
        for(ASTNode child = semicolon; child != null; child = child.getTreeNext()){
          if (STATEMENT_BIT_SET.contains(child.getElementType())) {
            return child;
          }
          if (child.getElementType() == RPARENTH) break;
        }
        return null;
      }

      case ChildRole.RPARENTH:
        return TreeUtil.findChild(this, RPARENTH);

      case ChildRole.LOOP_BODY:
      {
        ASTNode rparenth = findChildByRole(ChildRole.RPARENTH);
        for(ASTNode child = rparenth; child != null; child = child.getTreeNext()){
          if (STATEMENT_BIT_SET.contains(child.getElementType())) {
            return child;
          }
        }
        return null;
      }
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == FOR_KEYWORD) {
      return ChildRole.FOR_KEYWORD;
    }
    else if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else if (i == SEMICOLON) {
      return ChildRole.FOR_SEMICOLON;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.CONDITION;
      }
      else if (STATEMENT_BIT_SET.contains(child.getElementType())) {
        int role = getChildRole(child, ChildRole.FOR_INITIALIZATION);
        if (role != ChildRole.NONE) return role;
        role = getChildRole(child, ChildRole.FOR_UPDATE);
        if (role != ChildRole.NONE) return role;
        return ChildRole.LOOP_BODY;
      }
      else {
        return ChildRole.NONE;
      }
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitForStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiForStatement";
  }

  public void deleteChildInternal(@NotNull ASTNode child) {
    final boolean isForInitialization = getChildRole(child) == ChildRole.FOR_INITIALIZATION;

    if (isForInitialization) {
      try {
        final PsiStatement emptyStatement = JavaPsiFacade.getInstance(getManager().getProject()).getElementFactory().createStatementFromText(";", null);
        super.replaceChildInternal(child, (TreeElement)SourceTreeToPsiMap.psiElementToTree(emptyStatement));
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    else {
      super.deleteChildInternal(child);
    }
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null || lastParent.getParent() != this)
      // Parent element should not see our vars
      return true;

    return PsiScopesUtil.walkChildrenScopes(this, processor, substitutor, lastParent, place);
  }
}
