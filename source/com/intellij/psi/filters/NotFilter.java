package com.intellij.psi.filters;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import org.jdom.Element;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 19:24:33
 * To change this template use Options | File Templates.
 */
public class NotFilter
 implements ElementFilter{
  ElementFilter myFilter;

  public NotFilter(){}

  public NotFilter(ElementFilter filter){
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
    return !myFilter.isAcceptable(element, context);
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
    return "!" + getFilter();
  }

}
