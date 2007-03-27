package com.intellij.psi.filters.types;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ReflectionCache;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 05.06.2003
 * Time: 16:31:39
 * To change this template use Options | File Templates.
 */
public class ArrayTypeFilter implements ElementFilter{
  public boolean isAcceptable(Object element, PsiElement context){
    return element instanceof PsiArrayType;
  }

  public boolean isClassAcceptable(Class hintClass){
    return ReflectionCache.isAssignable(PsiArrayType.class, hintClass);
  }
}
