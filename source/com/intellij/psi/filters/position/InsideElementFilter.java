package com.intellij.psi.filters.position;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.position.PositionElementFilter;
import com.intellij.psi.filters.ElementFilter;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 25.03.2003
 * Time: 12:11:40
 * To change this template use Options | File Templates.
 */
public class InsideElementFilter extends PositionElementFilter{
  public InsideElementFilter(ElementFilter filter){
    setFilter(filter);
  }

  public InsideElementFilter(){}

  public boolean isAcceptable(Object element, PsiElement scope){
    if (!(element instanceof PsiElement)) return false;
    PsiElement currentChild = getOwnerChild(scope, (PsiElement) element);
    return getFilter().isAcceptable(currentChild, scope);
  }

  public String toString(){
    return "in(" + getFilter().toString() + ")";
  }
}
