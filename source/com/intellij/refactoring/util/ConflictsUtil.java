/**
 * created at Oct 8, 2001
 * @author Jeka
 */
package com.intellij.refactoring.util;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;

public class ConflictsUtil {
  public static PsiMember getContainer(PsiElement place) {
    PsiElement parent = place;
    while (true) {
      if (parent instanceof PsiMember && !(parent instanceof PsiTypeParameter))
        return (PsiMember)parent;
      if (parent instanceof PsiFile) return null;
      parent = parent.getParent();
    }
  }

  public static String getDescription(PsiElement element, boolean includeParent) {
    if (element instanceof PsiField) {
      int options = PsiFormatUtil.SHOW_NAME;
      if (includeParent) {
        options |= PsiFormatUtil.SHOW_CONTAINING_CLASS;
      }
      return "field " + htmlEmphasize(PsiFormatUtil.formatVariable((PsiVariable) element, options, PsiSubstitutor.EMPTY));
    }

    if (element instanceof PsiMethod) {
      int options = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS;
      if (includeParent) {
        options |= PsiFormatUtil.SHOW_CONTAINING_CLASS;
      }
      final PsiMethod method = (PsiMethod) element;
      final String descr = method.isConstructor() ? "constructor" : "method";
      return descr + " " + htmlEmphasize(PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, PsiFormatUtil.SHOW_TYPE));
    }

    if (element instanceof PsiClassInitializer) {
      PsiClassInitializer initializer = (PsiClassInitializer) element;
      boolean isStatic = initializer.hasModifierProperty(PsiModifier.STATIC);
      String s = isStatic ? "static initializer" : "instance initializer";
      if (includeParent) {
        s += " of class " + getDescription(initializer.getContainingClass(), false);
      }
      return s;
    }

    if (element instanceof PsiParameter) {
      return "parameter " + htmlEmphasize(((PsiParameter) element).getName());
    }

    if (element instanceof PsiLocalVariable) {
      return "local variable " + htmlEmphasize(((PsiVariable) element).getName());
    }

    if (element instanceof PsiPackage) {
      return "package " + htmlEmphasize(((PsiPackage) element).getName());
    }

    if ((element instanceof PsiClass)) {
      //TODO : local & anonymous
      PsiClass psiClass = (PsiClass) element;
      String qualifiedName = psiClass.getQualifiedName();
      if (qualifiedName != null) {
        return htmlEmphasize(qualifiedName);
      } else if(psiClass.getName() == null) {
        return htmlEmphasize("anonymous class");
      } else {
        return htmlEmphasize(psiClass.getName());
      }
    }

    return htmlEmphasize("???");


  }

  public static String htmlEmphasize(String text) {
    return "<b><code>" + text + "</code></b>";
  }

  public static String capitalize(String text) {
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }
}
