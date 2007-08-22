package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.position.PositionElementFilter;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 13:38:10
 * To change this template use Options | File Templates.
 */
public class ScopeFilter extends PositionElementFilter{
  public ScopeFilter(){}

  public ScopeFilter(ElementFilter filter){
    setFilter(filter);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    return context != null && getFilter().isAcceptable(context, context);
  }

  public String toString(){
    return "scope(" +getFilter()+")";
  }
}
