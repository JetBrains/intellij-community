package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.HashMap;

import java.util.Map;

public class PsiMethodCallExpressionImpl extends CompositePsiElement implements PsiMethodCallExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl");

  public PsiMethodCallExpressionImpl() {
    super(METHOD_CALL_EXPRESSION);
  }

  public PsiType getType() {
    PsiReferenceExpression methodExpression = getMethodExpression();
    final JavaResolveResult result = methodExpression.advancedResolve(false);
    final PsiMethod method = (PsiMethod)result.getElement();
    if (method == null) return null;

    PsiManager manager = getManager();
    if (manager.getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0) {
      //JLS3 15.8.2
      if ("getClass".equals(method.getName()) && "java.lang.Object".equals(method.getContainingClass().getQualifiedName())) {
        PsiExpression qualifier = methodExpression.getQualifierExpression();
        PsiType qualifierType = null;
        if (qualifier != null) {
          qualifierType = TypeConversionUtil.erasure(qualifier.getType());
        } else {
          ASTNode parent = getTreeParent();
          while(parent != null && parent.getElementType() != CLASS) parent = parent.getTreeParent();
          if (parent != null) {
            qualifierType = getManager().getElementFactory().createType((PsiClass)parent.getPsi());
          }
        }
        if (qualifierType != null) {
          PsiClass javaLangClass = manager.findClass("java.lang.Class", getResolveScope());
          if (javaLangClass != null && javaLangClass.getTypeParameters().length == 1) {
            Map<PsiTypeParameter, PsiType> map = new HashMap<PsiTypeParameter, PsiType>();
            map.put(javaLangClass.getTypeParameters()[0],
                    PsiWildcardType.createExtends(manager, qualifierType));
            PsiSubstitutor substitutor = manager.getElementFactory().createSubstitutor(map);
            return manager.getElementFactory().createType(javaLangClass, substitutor);
          }
        }
      }
    }

    final PsiType ret = method.getReturnType();
    if (ret == null) return null;
    if (getManager().getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0) {
      final PsiSubstitutor substitutor = result.getSubstitutor();
      if (PsiUtil.isRawSubstitutor(method, substitutor)) return TypeConversionUtil.erasure(ret);
      PsiType substitutedReturnType = substitutor.substituteAndCapture(ret);
      return PsiImplUtil.normalizeWildcardTypeByPosition(substitutedReturnType, this);
    }
    return TypeConversionUtil.erasure(ret);
  }

  public PsiMethod resolveMethod() {
    return (PsiMethod)getMethodExpression().resolve();
  }

  public JavaResolveResult resolveMethodGenerics() {
    return getMethodExpression().advancedResolve(false);
  }

  public PsiReferenceParameterList getTypeArgumentList() {
    return getMethodExpression().getParameterList();
  }

  public PsiType[] getTypeArguments() {
    return getMethodExpression().getTypeParameters();
  }

  public PsiReferenceExpression getMethodExpression() {
    return (PsiReferenceExpression)findChildByRoleAsPsiElement(ChildRole.METHOD_EXPRESSION);
  }

  public PsiExpressionList getArgumentList() {
    return (PsiExpressionList)findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.METHOD_EXPRESSION:
        return getFirstChildNode();

      case ChildRole.ARGUMENT_LIST:
        return TreeUtil.findChild(this, EXPRESSION_LIST);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == EXPRESSION_LIST) {
      return ChildRole.ARGUMENT_LIST;
    }
    else {
      if (EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.METHOD_EXPRESSION;
      }
      return ChildRole.NONE;
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitMethodCallExpression(this);
  }

  public String toString() {
    return "PsiMethodCallExpression:" + getText();
  }
}

