/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.psi.*;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.application.options.colors.ColorAndFontOptions;

public class HighlightNamesUtil {
  public static HighlightInfo highlightMethodName(PsiMethod method, PsiElement elementToHighlight, boolean isDeclaration) {
    HighlightInfoType type = getMethodNameHighlightType(method, isDeclaration);
    if (type != null && elementToHighlight != null) {
      TextAttributes attributes = getMergedAttributes(method, type);
      return HighlightInfo.createHighlightInfo(type, elementToHighlight.getTextRange(), null, attributes);
    }
    return null;
  }

  private static TextAttributes getMergedAttributes(final PsiElement element, final HighlightInfoType type) {
    TextAttributes regularAttributes = HighlightInfo.getAttributesByType(type);
    if (element == null) return regularAttributes;
    TextAttributes scopeAttributes = getScopeAttributes(element);
    TextAttributes attributes = TextAttributes.merge(scopeAttributes, regularAttributes);
    return attributes;
  }

  public static HighlightInfo highlightClassName(PsiClass aClass, PsiElement elementToHighlight) {
    HighlightInfoType type = getClassNameHighlightType(aClass);
    if (type != null && elementToHighlight != null) {
      TextAttributes attributes = getMergedAttributes(aClass, type);
      return HighlightInfo.createHighlightInfo(type, elementToHighlight.getTextRange(), null, attributes);
    }
    return null;
  }

  public static HighlightInfo highlightVariable(PsiVariable variable, PsiElement elementToHighlight) {
    HighlightInfoType varType = getVariableNameHighlightType(variable);
    if (varType != null) {
      if (variable instanceof PsiField) {
        TextAttributes attributes = getMergedAttributes(variable, varType);
        return HighlightInfo.createHighlightInfo(varType, elementToHighlight.getTextRange(), null, attributes);
      }
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
      PsiElement resolved = ((PsiJavaCodeReferenceElement)qualifierExpression).resolve();
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
    if (var instanceof PsiLocalVariable
        || var instanceof PsiParameter && ((PsiParameter)var).getDeclarationScope() instanceof PsiForeachStatement) {
      return HighlightInfoType.LOCAL_VARIABLE;
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
    PsiJavaCodeReferenceElement nameRefElement = annotation.getNameReferenceElement();
    if (nameRefElement != null) {
      HighlightInfoType type = HighlightInfoType.ANNOTATION_NAME;
      TextAttributes attributes = getMergedAttributes(annotation, type);
      return HighlightInfo.createHighlightInfo(type, nameRefElement.getTextRange(), null, attributes);
    }
    return null;
  }

  public static HighlightInfo highlightReassignedVariable(PsiVariable variable, PsiElement elementToHighlight) {
    if (variable instanceof PsiLocalVariable) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.REASSIGNED_LOCAL_VARIABLE, elementToHighlight, null);
    }
    else if (variable instanceof PsiParameter) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.REASSIGNED_PARAMETER, elementToHighlight, null);
    }
    else {
      return null;
    }
  }

  private static TextAttributes getScopeAttributes(PsiElement element) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    NamedScopeManager namedScopeManager = NamedScopeManager.getInstance(element.getProject());
    NamedScope[] scopes = namedScopeManager.getScopes();
    PsiFile file = element.getContainingFile();
    if (file == null) return null;
    for (NamedScope namedScope : scopes) {
      PackageSet packageSet = namedScope.getValue();
      String name = namedScope.getName();
      if (packageSet.contains(file, namedScopeManager)) {
        TextAttributesKey scopeKey = ColorAndFontOptions.getScopeTextAttributeKey(name);
        TextAttributes attributes = scheme.getAttributes(scopeKey);
        if (attributes != null) return attributes;
      }
    }
    return null;
  }
}