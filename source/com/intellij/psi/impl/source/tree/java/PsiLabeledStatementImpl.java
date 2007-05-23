package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class PsiLabeledStatementImpl extends CompositePsiElement implements PsiLabeledStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiLabeledStatementImpl");

  public PsiLabeledStatementImpl() {
    super(LABELED_STATEMENT);
  }

  @NotNull
  public PsiIdentifier getLabelIdentifier() {
    return (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.LABEL_NAME);
  }

  public PsiStatement getStatement() {
    return (PsiStatement)findChildByRoleAsPsiElement(ChildRole.STATEMENT);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.STATEMENT:
        return TreeUtil.findChild(this, STATEMENT_BIT_SET);

      case ChildRole.COLON:
        return TreeUtil.findChild(this, COLON);

      case ChildRole.LABEL_NAME:
        return getFirstChildNode();
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == IDENTIFIER) {
      return ChildRole.LABEL_NAME;
    }
    else if (i == COLON) {
      return ChildRole.COLON;
    }
    else {
      if (STATEMENT_BIT_SET.contains(child.getElementType())) {
        return ChildRole.STATEMENT;
      }
      return ChildRole.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitLabeledStatement(this);
  }

  public String toString() {
    return "PsiLabeledStatement";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    if (lastParent != null && lastParent.getParent() != this){
      PsiElement[] children = getChildren();
      for (PsiElement aChildren : children) {
        if (!PsiScopesUtil.processScope(aChildren, processor, substitutor, null, place)) {
          return false;
        }
      }
    }
    return true;
  }

  public String getName() {
    return getLabelIdentifier().getText();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    SharedPsiElementImplUtil.setName(getLabelIdentifier(), name);
    return this;
  }
}
