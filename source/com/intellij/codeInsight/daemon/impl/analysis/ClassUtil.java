/**
 * @author Alexey
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiSuperMethodUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ClassUtil {
  public static PsiMethod getAnyAbstractMethod(PsiClass aClass, MethodSignatureUtil.MethodSignatureToMethods allMethodsCollection) {
    final PsiMethod methodToImplement = getAnyMethodToImplement(aClass, allMethodsCollection);
    if (methodToImplement != null) {
      return methodToImplement;
    }
    final PsiMethod[] methods = aClass.getMethods();
    for (int i = 0; i < methods.length; i++) {
      PsiMethod method = methods[i];
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return method;
    }

    // the only remaining possiblity for class to have abstract method here is
    //  from package local abstract method defined in inherited class from other package
    final PsiManager manager = aClass.getManager();
    for (Iterator<List<MethodSignatureBackedByPsiMethod>> iterator = allMethodsCollection.values().iterator(); iterator.hasNext();) {
      List<MethodSignatureBackedByPsiMethod> sameSignatureMethods = iterator.next();

      // look for abstract package locals
      for (int i = sameSignatureMethods.size() - 1; i >= 0; i--) {
        final MethodSignatureBackedByPsiMethod methodSignature1 = sameSignatureMethods.get(i);
        PsiMethod method1 = methodSignature1.getMethod();
        PsiClass class1 = method1.getContainingClass();
        if (class1 == null) {
          sameSignatureMethods.remove(i);
          continue;
        }
        if (!method1.hasModifierProperty(PsiModifier.ABSTRACT)
            || !method1.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
            || manager.arePackagesTheSame(class1, aClass)) {
          continue;
        }
        // check if abstract package local method gets overriden by not abstract
        // i.e. there is direct subclass in the same package which overrides this method
        for (int j = sameSignatureMethods.size() - 1; j >= 0; j--) {
          final MethodSignatureBackedByPsiMethod methodSignature2 = sameSignatureMethods.get(j);
          PsiMethod method2 = methodSignature2.getMethod();
          PsiClass class2 = method2.getContainingClass();
          if (i == j || class2 == null) continue;
          if (class2.isInheritor(class1, true)
              // NB! overriding method may be abstract
//              && !method2.hasModifierProperty(PsiModifier.ABSTRACT)
              && manager.arePackagesTheSame(class1, class2)) {
            sameSignatureMethods.remove(i);
            break;
          }
        }
      }
      for (int i = sameSignatureMethods.size() - 1; i >= 0; i--) {
        final MethodSignatureBackedByPsiMethod methodSignature = sameSignatureMethods.get(i);
        PsiMethod method = methodSignature.getMethod();
        PsiClass class1 = method.getContainingClass();
        if (class1 == null
            || !method.hasModifierProperty(PsiModifier.ABSTRACT)
            || !method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
            || manager.arePackagesTheSame(class1, aClass)) {
          continue;
        }
        return method;
      }
    }
    return null;
  }

  public static PsiMethod getAnyMethodToImplement(PsiClass aClass, MethodSignatureUtil.MethodSignatureToMethods allMethodsCollection) {
    for (Iterator<List<MethodSignatureBackedByPsiMethod>> iterator = allMethodsCollection.values().iterator(); iterator.hasNext();) {
      List<MethodSignatureBackedByPsiMethod> sameSignatureMethods = new ArrayList<MethodSignatureBackedByPsiMethod>(iterator.next());
      PsiSuperMethodUtil.removeOverriddenMethods(sameSignatureMethods, aClass, aClass);
      if (sameSignatureMethods.size() != 0) {
        final MethodSignatureBackedByPsiMethod methodSignature = sameSignatureMethods.get(0);
        final PsiMethod method = methodSignature.getMethod();
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || aClass.equals(containingClass)) {
          continue;
        }
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)
            && !method.hasModifierProperty(PsiModifier.STATIC)
            && !method.hasModifierProperty(PsiModifier.PRIVATE)
            && aClass.getManager().getResolveHelper().isAccessible(method, aClass, aClass)) {
          return method;
        }
      }
    }
    return null;
  }

  public static TextRange getClassDeclarationTextRange(PsiClass aClass) {
    if (aClass instanceof PsiEnumConstantInitializer) {
      return aClass.getLBrace().getTextRange();
    }
    TextRange startTextRange = (aClass instanceof PsiAnonymousClass
                     ? ((PsiAnonymousClass)aClass).getBaseClassReference()
                     : aClass.getModifierList() == null ? (PsiElement)aClass.getNameIdentifier() : aClass.getModifierList()).getTextRange();
    int start = startTextRange == null ? 0 : startTextRange.getStartOffset();
    TextRange endTextRange = (aClass instanceof PsiAnonymousClass
                   ? (PsiElement)((PsiAnonymousClass)aClass).getBaseClassReference()
                   : aClass.getImplementsList()).getTextRange();
    int end = endTextRange == null ? start : endTextRange.getEndOffset();
    return new TextRange(start, end);
  }
}