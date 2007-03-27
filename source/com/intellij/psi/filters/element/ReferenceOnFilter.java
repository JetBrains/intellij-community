package com.intellij.psi.filters.element;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.PositionElementFilter;
import com.intellij.util.ReflectionCache;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 07.02.2003
 * Time: 17:25:04
 * To change this template use Options | File Templates.
 */
public class ReferenceOnFilter extends PositionElementFilter{
  public ReferenceOnFilter(ElementFilter filter){
    setFilter(filter);
  }

  public boolean isClassAcceptable(Class hintClass){
    return ReflectionCache.isAssignable(PsiJavaCodeReferenceElement.class, hintClass);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if (!(element instanceof PsiElement)) return false;
    PsiElement parent = ((PsiElement) element).getParent();
    return parent instanceof PsiJavaCodeReferenceElement &&
           getFilter().isAcceptable(((PsiJavaReference)parent).advancedResolve(true).getElement(), context);
  }
}
