package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiThisExpressionImpl extends ExpressionPsiElement implements PsiThisExpression, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiThisExpressionImpl");

  public PsiThisExpressionImpl() {
    super(THIS_EXPRESSION);
  }

  public PsiJavaCodeReferenceElement getQualifier() {
    return (PsiJavaCodeReferenceElement)findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  public PsiType getType() {
    PsiJavaCodeReferenceElement qualifier = getQualifier();
    if (qualifier != null){
      PsiClass qualifierResolve = (PsiClass)qualifier.resolve();
      if (qualifierResolve != null) return new PsiImmediateClassType(qualifierResolve, PsiSubstitutor.EMPTY);

      return new PsiClassReferenceType(qualifier);
    }
    for(PsiElement scope = getContext(); scope != null; scope = scope.getContext()){
      if (scope instanceof PsiClass){
        PsiClass aClass = (PsiClass)scope;
        return new PsiImmediateClassType(aClass, PsiSubstitutor.EMPTY);
      }
      else if (scope instanceof PsiExpressionList && scope.getParent() instanceof PsiAnonymousClass){
        scope = scope.getParent();
      }
      else if (scope instanceof PsiCodeFragment){
        PsiType fragmentThisType = ((PsiCodeFragment)scope).getThisType();
        if (fragmentThisType != null) return fragmentThisType;
      }
    }
    return null;
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.QUALIFIER:
        if (getFirstChildNode().getElementType() == JAVA_CODE_REFERENCE){
          return getFirstChildNode();
        }
        else{
          return null;
        }

      case ChildRole.DOT:
        return TreeUtil.findChild(this, DOT);

      case ChildRole.THIS_KEYWORD:
        return getLastChildNode();
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JAVA_CODE_REFERENCE) {
      return ChildRole.QUALIFIER;
    }
    else if (i == DOT) {
      return ChildRole.DOT;
    }
    else if (i == THIS_KEYWORD) {
      return ChildRole.THIS_KEYWORD;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitThisExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiThisExpression:" + getText();
  }
}

