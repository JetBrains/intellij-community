package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 19:11:46
 * To change this template use Options | File Templates.
 */
public class AndFilter implements ElementFilter{
  private final List<ElementFilter> myFilters = new ArrayList<ElementFilter>();

  public AndFilter(ElementFilter filter1, ElementFilter filter2){
    this(new ElementFilter[]{filter1, filter2});
  }

  public AndFilter(ElementFilter... filters){
    for (ElementFilter filter : filters) {
      addFilter(filter);
    }
  }

  private void addFilter(ElementFilter filter){
    myFilters.add(filter);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    for (ElementFilter elementFilter : myFilters) {
      if (!elementFilter.isAcceptable(element, context)) {
        return false;
      }
    }
    return true;
  }

  public boolean isClassAcceptable(Class elementClass){
    for (Object myFilter : myFilters) {
      final ElementFilter elementFilter = (ElementFilter)myFilter;
      if (!elementFilter.isClassAcceptable(elementClass)) {
        return false;
      }
    }
    return true;
  }

  public String toString(){
    String ret = "(";
    Iterator iter = myFilters.iterator();
    while(iter.hasNext()){
      ret += iter.next();
      if(iter.hasNext()){
        ret += " & ";
      }
    }
    ret += ")";
    return ret;
  }
}
