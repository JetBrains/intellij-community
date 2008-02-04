/*
 * User: anna
 * Date: 04-Feb-2008
 */
package com.intellij.ide.navigationToolbar;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.lang.StdLanguages;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;

public class JavaNavBarExtension implements NavBarModelExtension{
  public String getPresentableText(final Object object) {
    if (object instanceof PsiClass) {
      return ClassPresentationUtil.getNameForClass((PsiClass)object, false);
    }
    else if (object instanceof PsiPackage) {
      final String name = ((PsiPackage)object).getName();
      return name != null ? name : AnalysisScopeBundle.message("dependencies.tree.node.default.package.abbreviation");
    }
    return null;
  }

  public PsiElement getParent(final PsiElement psiElement) {
    if (psiElement instanceof PsiPackage) {
      final PsiPackage parentPackage = ((PsiPackage)psiElement).getParentPackage();
      if (parentPackage != null && parentPackage.getQualifiedName().length() > 0) {
        return parentPackage;
      }
    }
    return null;
  }


  public PsiElement adjustElement(final PsiElement psiElement) {
    if (psiElement instanceof PsiJavaFile) {
      final PsiJavaFile psiJavaFile = (PsiJavaFile)psiElement;
      if (psiJavaFile.getViewProvider().getBaseLanguage() == StdLanguages.JAVA) {
        final PsiClass[] psiClasses = psiJavaFile.getClasses();
        if (psiClasses.length == 1) {
          return psiClasses[0];
        }
      }
    }
    if (psiElement instanceof PsiClass) return psiElement;
    final PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile != null) return containingFile;
    return psiElement;
  }
}