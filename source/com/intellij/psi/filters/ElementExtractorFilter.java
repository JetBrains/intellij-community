package com.intellij.psi.filters;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.infos.CandidateInfo;
import org.jdom.Element;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 17.07.2003
 * Time: 13:43:17
 * To change this template use Options | File Templates.
 */
public class ElementExtractorFilter implements ElementFilter{
  ElementFilter myFilter;

  public ElementExtractorFilter(){}

  public ElementExtractorFilter(ElementFilter filter){
    myFilter = filter;
  }

  public void setFilter(ElementFilter filter){
    myFilter = filter;
  }

  public ElementFilter getFilter(){
    return myFilter;
  }

  public boolean isClassAcceptable(Class hintClass){
    return myFilter.isClassAcceptable(hintClass);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof CandidateInfo)
      return myFilter.isAcceptable(((CandidateInfo)element).getElement(), context);
    else if(myFilter instanceof PsiElement)
      return myFilter.isAcceptable(element, context);
    return false;
  }


  public void readExternal(Element element)
    throws InvalidDataException{
    myFilter = (ElementFilter)FilterUtil.readFilterGroup(element).get(0);
  }

  public void writeExternal(Element element)
    throws WriteExternalException{
    throw new WriteExternalException("Filter data could _not_ be written");
  }


  public String toString(){
    return getFilter().toString();
  }
}
