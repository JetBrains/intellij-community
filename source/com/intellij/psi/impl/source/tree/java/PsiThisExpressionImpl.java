package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.jsp.JspFileImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.jsp.JspFile;

public class PsiThisExpressionImpl extends CompositePsiElement implements PsiThisExpression {
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
      else if (scope instanceof JspFileImpl){
        // Not too correct but much better then HttpJspPage :)
        PsiClass baseClass = ((JspFileImpl)scope).getBaseClass();
        if(baseClass == null) baseClass = getManager().findClass("javax.servlet.jsp.HttpJspPage", getResolveScope());
        if(baseClass == null) return PsiType.getJavaLangObject(getManager(), getResolveScope());
        final PsiClassType type = getManager().getElementFactory().createType(baseClass);
        return type;
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

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.QUALIFIER:
        if (firstChild.getElementType() == JAVA_CODE_REFERENCE){
          return firstChild;
        }
        else{
          return null;
        }

      case ChildRole.DOT:
        return TreeUtil.findChild(this, DOT);

      case ChildRole.THIS_KEYWORD:
        return lastChild;
    }
  }

  public int getChildRole(TreeElement child) {
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

  public void accept(PsiElementVisitor visitor) {
    visitor.visitThisExpression(this);
  }

  public String toString() {
    return "PsiThisExpression:" + getText();
  }
}

