package com.intellij.psi.filters.position;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.ElementFilter;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 03.02.2003
 * Time: 18:54:57
 * To change this template use Options | File Templates.
 */
public class ParentSkipReferenceElementFilter extends PositionElementFilter{
  public ParentSkipReferenceElementFilter(ElementFilter filter){
    setFilter(filter);
  }

  public ParentSkipReferenceElementFilter(){}

  public boolean isAcceptable(Object element, PsiElement scope){
    if (!(element instanceof PsiElement)) return false;
    final PsiElement context = ((PsiElement)element).getContext();
    if(context != null){
      if(context instanceof PsiReference)
        return isAcceptable(context, scope);
      return getFilter().isAcceptable(context, scope);
    }
    return false;
  }


  public String toString(){
    return "parent(" +getFilter()+")";
  }

}
