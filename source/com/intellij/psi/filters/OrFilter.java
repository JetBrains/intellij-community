package com.intellij.psi.filters;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 19:23:44
 * To change this template use Options | File Templates.
 */

public class OrFilter
 implements ElementFilter{
  private List myFilters = new ArrayList();

  public OrFilter(){}

  public OrFilter(ElementFilter[] filters){
    for(int i = 0; i < filters.length; i++){
      addFilter(filters[i]);
    }
  }

  public OrFilter(ElementFilter filter1, ElementFilter filter2){
    this(new ElementFilter[]{filter1, filter2});
  }

  public void addFilter(ElementFilter filter){
    myFilters.add(filter);
  }

  public List getFilters(){
    return myFilters;
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(myFilters.isEmpty())
      return true;
    for(int i = 0; i < myFilters.size(); i++){
      final ElementFilter elementFilter = (ElementFilter) myFilters.get(i);
      if(elementFilter.isAcceptable(element, context)){
        return true;
      }
    }
    return false;
  }

  public boolean isClassAcceptable(Class elementClass){
    if(myFilters.isEmpty())
      return true;
    for(int i = 0; i < myFilters.size(); i++){
      final ElementFilter elementFilter = (ElementFilter) myFilters.get(i);
      if(elementFilter.isClassAcceptable(elementClass)){
        return true;
      }
    }
    return false;
  }

  public void readExternal(Element element)
    throws InvalidDataException{
    myFilters = FilterUtil.readFilterGroup(element);
  }

  public void writeExternal(Element element)
    throws WriteExternalException{
    throw new WriteExternalException("Filter data could _not_ be written");
  }


  public String toString(){
    String ret = "(";
    Iterator iter = myFilters.iterator();
    while(iter.hasNext()){
      ret += iter.next();
      if(iter.hasNext()){
        ret += " | ";
      }
    }
    ret += ")";
    return ret;
  }
}
