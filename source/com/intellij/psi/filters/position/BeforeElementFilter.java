package com.intellij.psi.filters.position;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 03.02.2003
 * Time: 18:29:13
 * To change this template use Options | File Templates.
 */
public class BeforeElementFilter extends PositionElementFilter{
  public BeforeElementFilter(ElementFilter filter){
    setFilter(filter);
  }

  public BeforeElementFilter(){}
  public boolean isAcceptable(Object element, PsiElement scope){
    if (!(element instanceof PsiElement)) return false;
    final PsiElement ownerChild = getOwnerChild(scope, (PsiElement) element);
    if(ownerChild == null) return false;
    PsiElement currentChild = ownerChild.getNextSibling();
    while(currentChild != null){
      if(getFilter().isAcceptable(currentChild, scope)){
        return true;
      }
      currentChild = currentChild.getNextSibling();
    }
    return false;
  }

  public String toString(){
    return "before(" + getFilter().toString() + ")";
  }
}
