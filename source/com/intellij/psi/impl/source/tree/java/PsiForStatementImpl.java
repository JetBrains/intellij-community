package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.util.CharTable;

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

  public TreeElement findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.FOR_KEYWORD:
        return TreeUtil.findChild(this, FOR_KEYWORD);

      case ChildRole.LPARENTH:
        return TreeUtil.findChild(this, LPARENTH);

      case ChildRole.FOR_INITIALIZATION:
        final TreeElement initialization = TreeUtil.findChild(this, STATEMENT_BIT_SET);
        // should be inside parens
        for(TreeElement child = initialization; child != null; child = child.getTreeNext()){
          if (child.getElementType() == RPARENTH) return initialization;
        }
        return null;

      case ChildRole.CONDITION:
        return TreeUtil.findChild(this, EXPRESSION_BIT_SET);

      case ChildRole.FOR_SEMICOLON:
        return TreeUtil.findChild(this, SEMICOLON);

      case ChildRole.FOR_UPDATE:
      {
        TreeElement semicolon = findChildByRole(ChildRole.FOR_SEMICOLON);
        for(TreeElement child = semicolon; child != null; child = child.getTreeNext()){
          if (STATEMENT_BIT_SET.isInSet(child.getElementType())) {
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
        TreeElement rparenth = findChildByRole(ChildRole.RPARENTH);
        for(TreeElement child = rparenth; child != null; child = child.getTreeNext()){
          if (STATEMENT_BIT_SET.isInSet(child.getElementType())) {
            return child;
          }
        }
        return null;
      }
    }
  }

  public int getChildRole(TreeElement child) {
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
      if (EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.CONDITION;
      }
      else if (STATEMENT_BIT_SET.isInSet(child.getElementType())) {
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

  public void accept(PsiElementVisitor visitor){
    visitor.visitForStatement(this);
  }

  public String toString(){
    return "PsiForStatement";
  }

  public void deleteChildInternal(TreeElement child) {
    final boolean isForInitialization = getChildRole(child) == ChildRole.FOR_INITIALIZATION;

    if (isForInitialization) {
      final CompositeElement emptyStatement = Factory.createCompositeElement(EMPTY_STATEMENT);
      final LeafElement comma = Factory.createSingleLeafElement(SEMICOLON, new char[]{';'}, 0, 1, SharedImplUtil.findCharTableByTree(this), getManager());
      emptyStatement.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(comma));
      TreeUtil.addChildren(emptyStatement, comma);
      super.replaceChildInternal(child, emptyStatement);
    }
    else {
      super.deleteChildInternal(child);
    }
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null || lastParent.getParent() != this)
      // Parent element should not see our vars
      return true;

    return PsiScopesUtil.walkChildrenScopes(this, processor, substitutor, lastParent, place);
  }
}
