package com.intellij.psi.filters.types;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.filters.ElementFilter;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.08.2003
 * Time: 17:54:00
 * To change this template use Options | File Templates.
 */
public class TypeFilter implements ElementFilter{
  private Object myType;

  public TypeFilter(Object type){
    myType = type;
  }

  public boolean isAcceptable(Object element, PsiElement context){
    return ((PsiType)element).equals(myType);
  }

  public boolean isClassAcceptable(Class hintClass){
    return PsiType.class.isAssignableFrom(hintClass);
  }
}
