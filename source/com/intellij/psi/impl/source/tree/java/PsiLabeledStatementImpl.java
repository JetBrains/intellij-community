package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.util.IncorrectOperationException;

public class PsiLabeledStatementImpl extends CompositePsiElement implements PsiLabeledStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiLabeledStatementImpl");

  public PsiLabeledStatementImpl() {
    super(LABELED_STATEMENT);
  }

  public PsiIdentifier getLabelIdentifier() {
    return (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.LABEL_NAME);
  }

  public PsiStatement getStatement() {
    return (PsiStatement)findChildByRoleAsPsiElement(ChildRole.STATEMENT);
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.STATEMENT:
        return TreeUtil.findChild(this, STATEMENT_BIT_SET);

      case ChildRole.COLON:
        return TreeUtil.findChild(this, COLON);

      case ChildRole.LABEL_NAME:
        return firstChild;
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == IDENTIFIER) {
      return ChildRole.LABEL_NAME;
    }
    else if (i == COLON) {
      return ChildRole.COLON;
    }
    else {
      if (STATEMENT_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.STATEMENT;
      }
      return ChildRole.NONE;
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitLabeledStatement(this);
  }

  public String toString() {
    return "PsiLabeledStatement";
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    if (lastParent != null && lastParent.getParent() != this){
      PsiElement[] children = getChildren();
      for(int i = 0; i < children.length; i++){
        if (!PsiScopesUtil.processScope(children[i], processor, substitutor, null, place)){
          return false;
        }
      }
    }
    return true;
  }

  public String getName() {
    return getLabelIdentifier().getText();
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    SharedPsiElementImplUtil.setName(getLabelIdentifier(), name);
    return this;
  }
}
