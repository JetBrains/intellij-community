package com.intellij.psi.filters;

import com.intellij.openapi.util.InvalidDataException;
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
  private List<ElementFilter> myFilters = new ArrayList<ElementFilter>();

  public OrFilter(){}

  public OrFilter(ElementFilter... filters){
    for (ElementFilter filter : filters) {
      addFilter(filter);
    }
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
    for (Object filter : myFilters) {
      final ElementFilter elementFilter = (ElementFilter)filter;
      if (elementFilter.isAcceptable(element, context)) {
        return true;
      }
    }
    return false;
  }

  public boolean isClassAcceptable(Class elementClass){
    if(myFilters.isEmpty())
      return true;
    for (Object myFilter : myFilters) {
      final ElementFilter elementFilter = (ElementFilter)myFilter;
      if (elementFilter.isClassAcceptable(elementClass)) {
        return true;
      }
    }
    return false;
  }

  public void readExternal(Element element)
    throws InvalidDataException{
    myFilters = FilterUtil.readFilterGroup(element);
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
