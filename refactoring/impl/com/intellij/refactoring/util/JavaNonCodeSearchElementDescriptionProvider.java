package com.intellij.refactoring.util;

import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class JavaNonCodeSearchElementDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(final PsiElement element, @Nullable final ElementDescriptionLocation location) {
    if (!(location instanceof NonCodeSearchDescriptionLocation)) return null;
    NonCodeSearchDescriptionLocation ncdLocation = (NonCodeSearchDescriptionLocation) location;
    if (element instanceof PsiPackage) {
      return ncdLocation.isNonJava() ? ((PsiPackage)element).getQualifiedName() : ((PsiPackage)element).getName();
    }
    else if (element instanceof PsiClass) {
      return ncdLocation.isNonJava() ? ((PsiClass)element).getQualifiedName() : ((PsiClass)element).getName();
    }
    else if (element instanceof PsiMember) {
      PsiMember member = (PsiMember)element;
      String name = member.getName();
      if (name == null) return null;
      if (!ncdLocation.isNonJava()) {
        return name;
      }
      PsiClass containingClass = member.getContainingClass();
      if (containingClass == null || containingClass.getQualifiedName() == null) return null;
      return containingClass.getQualifiedName() + "." + name;
    }
    return null;
  }
}
