package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import org.jetbrains.annotations.NotNull;

public class PsiSuperExpressionImpl extends ExpressionPsiElement implements PsiSuperExpression, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiSuperExpressionImpl");

  public PsiSuperExpressionImpl() {
    super(SUPER_EXPRESSION);
  }

  public PsiJavaCodeReferenceElement getQualifier() {
    return (PsiJavaCodeReferenceElement)findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  public PsiType getType() {
    PsiJavaCodeReferenceElement qualifier = getQualifier();
    if (qualifier != null){
      PsiClass aClass = (PsiClass)qualifier.resolve();
      if (aClass == null) return null;
      return getSuperType(aClass);
    }
    for(PsiElement scope = getContext(); scope != null; scope = scope.getContext()){
      if (scope instanceof PsiClass){
        PsiClass aClass = (PsiClass)scope;
        return getSuperType(aClass);
      }
      if (scope instanceof PsiExpressionList && scope.getParent() instanceof PsiAnonymousClass){
        scope = scope.getParent();
      }
      else if (scope instanceof JavaCodeFragment) {
        PsiType fragmentSuperType = ((JavaCodeFragment)scope).getSuperType();
        if (fragmentSuperType != null) return fragmentSuperType;
      }
    }
    return null;
  }

  private PsiType getSuperType(PsiClass aClass) {
    if (aClass.isInterface()) {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      return facade.getElementFactory().createType(facade.findClass("java.lang.Object", getResolveScope()));
    }

    if (aClass instanceof PsiAnonymousClass) {
      final PsiClassType baseClassType = ((PsiAnonymousClass)aClass).getBaseClassType();
      final PsiClass psiClass = baseClassType.resolve();
      if(psiClass != null && !psiClass.isInterface()){
        return baseClassType;
      }

      return PsiType.getJavaLangObject(getManager(), getResolveScope());
    }

    if ("java.lang.Object".equals(aClass.getQualifiedName())) return null;
    PsiClassType[] superTypes = aClass.getExtendsListTypes();
    if (superTypes.length == 0) {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      final PsiClass javaLangObject = facade.findClass("java.lang.Object", getResolveScope());
      if (javaLangObject != null) {
        return facade.getElementFactory().createType(javaLangObject);
      }
      else {
        return null;
      }
    }

    return superTypes[0];
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

      case ChildRole.SUPER_KEYWORD:
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
    else if (i == SUPER_KEYWORD) {
      return ChildRole.SUPER_KEYWORD;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSuperExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiSuperExpression:" + getText();
  }
}