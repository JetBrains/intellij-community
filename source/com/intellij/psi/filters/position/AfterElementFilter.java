package com.intellij.psi.filters.position;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 03.02.2003
 * Time: 18:33:46
 * To change this template use Options | File Templates.
 */
public class AfterElementFilter extends PositionElementFilter{
  public AfterElementFilter(ElementFilter filter){
    setFilter(filter);
  }

  public boolean isAcceptable(Object element, PsiElement scope){
    if (!(element instanceof PsiElement)) return false;
    PsiElement currentChild = getOwnerChild(scope, (PsiElement) element);
    PsiElement currentElement = scope.getFirstChild();
    while(currentElement != null){
      if(currentElement == currentChild)
        break;
      if(getFilter().isAcceptable(currentElement, scope)){
        return true;
      }
      currentElement = currentElement.getNextSibling();
    }
    return false;
  }

  public String toString(){
    return "after(" + getFilter().toString() + ")";
  }
}
