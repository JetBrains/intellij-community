/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.psi.*;

public class HighlightNamesUtil {
  public static HighlightInfo highlightMethodName(PsiMethod method, PsiElement elementToHighlight, boolean isDeclaration) {
    HighlightInfoType type = getMethodNameHighlightType(method, isDeclaration);
    if (type != null && elementToHighlight != null) {
      return HighlightInfo.createHighlightInfo(type, elementToHighlight, null);
    }
    return null;
  }

  public static HighlightInfo highlightClassName(PsiClass aClass, PsiElement elementToHighlight) {
    HighlightInfoType type = getClassNameHighlightType(aClass);
    if (type != null && elementToHighlight != null) {
      return HighlightInfo.createHighlightInfo(type, elementToHighlight, null);
    }
    return null;
  }

  public static HighlightInfo highlightVariable(final PsiVariable variable, PsiElement elementToHighlight) {
    HighlightInfoType varType = getVariableNameHighlightType(variable);
    if (varType != null) {
      return HighlightInfo.createHighlightInfo(varType, elementToHighlight, null);
    }
    return null;
  }

  public static HighlightInfo highlightClassNameInQualifier(PsiJavaCodeReferenceElement element) {
    PsiExpression qualifierExpression = null;
    if (element instanceof PsiReferenceExpression) {
      qualifierExpression = ((PsiReferenceExpression)element).getQualifierExpression();
    }
    if (qualifierExpression instanceof PsiJavaCodeReferenceElement) {
      final PsiElement resolved = ((PsiJavaCodeReferenceElement)qualifierExpression).resolve();
      if (resolved instanceof PsiClass) {
        return highlightClassName((PsiClass)resolved, qualifierExpression);
      }
    }
    return null;
  }

  private static HighlightInfoType getMethodNameHighlightType(PsiMethod method, boolean isDeclaration) {
    if (method.isConstructor()) {
      return isDeclaration ? HighlightInfoType.CONSTRUCTOR_DECLARATION : HighlightInfoType.CONSTRUCTOR_CALL;
    }
    if (isDeclaration) return HighlightInfoType.METHOD_DECLARATION;
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      return HighlightInfoType.STATIC_METHOD;
    }
    return HighlightInfoType.METHOD_CALL;
  }

  private static HighlightInfoType getVariableNameHighlightType(PsiVariable var) {
    if (var instanceof PsiLocalVariable || (var instanceof PsiParameter && ((PsiParameter)var).getDeclarationScope() instanceof PsiForeachStatement)) {
      return HighlightInfoType.LOCAL_VAR;
    }
    else if (var instanceof PsiField) {
      return var.hasModifierProperty(PsiModifier.STATIC)
          ? HighlightInfoType.STATIC_FIELD
          : HighlightInfoType.INSTANCE_FIELD;
    }
    else if (var instanceof PsiParameter) {
      return HighlightInfoType.PARAMETER;
    }
    else { //?
      return null;
    }
  }

  private static HighlightInfoType getClassNameHighlightType(PsiClass aClass) {
    // use class by default
    return aClass != null && aClass.isInterface() ? HighlightInfoType.INTERFACE_NAME : HighlightInfoType.CLASS_NAME;
  }

  public static HighlightInfo highlightAnnotationName(PsiAnnotation annotation) {
    final PsiJavaCodeReferenceElement nameRefElement = annotation.getNameReferenceElement();
    if (nameRefElement != null) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ANNOTATION_NAME, nameRefElement, null);
    }
    return null;
  }


}