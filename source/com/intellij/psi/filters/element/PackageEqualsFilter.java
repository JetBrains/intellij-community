package com.intellij.psi.filters.element;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ReflectionCache;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 14:37:06
 * To change this template use Options | File Templates.
 */
public class PackageEqualsFilter
  implements ElementFilter{

  public boolean isClassAcceptable(Class hintClass){
    return ReflectionCache.isAssignable(PsiClass.class, hintClass) || ReflectionCache.isAssignable(PsiPackage.class, hintClass);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if (!(element instanceof PsiElement)) return false;
    final String elementPackName = getPackageName((PsiElement) element);
    final String contextPackName = getPackageName(context);
    return elementPackName != null && contextPackName != null && elementPackName.equals(contextPackName);
  }

  protected static String getPackageName(PsiElement element){
    if(element instanceof PsiPackage){
      return ((PsiPackage)element).getQualifiedName();
    }
    if(element.getContainingFile() instanceof PsiJavaFile){
      return ((PsiJavaFile)element.getContainingFile()).getPackageName();
    }
    return null;
  }


  public String toString(){
    return "same-package";
  }
}
