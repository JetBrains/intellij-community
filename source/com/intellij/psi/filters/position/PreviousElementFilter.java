package com.intellij.psi.filters.position;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 13:48:00
 * To change this template use Options | File Templates.
 */
public class PreviousElementFilter extends PositionElementFilter{
  public PreviousElementFilter(){}

  public PreviousElementFilter(ElementFilter filter){
    setFilter(filter);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if (!(element instanceof PsiElement)) return false;
    if((element = FilterUtil.getPreviousElement((PsiElement) element, true)) != null){
      return getFilter().isAcceptable(element, context);
    }
    return false;
  }

  public String toString(){
    return "previous(" +getFilter()+")";
  }
}
