package com.intellij.psi.filters.getters;

import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.PsiElement;
import com.intellij.codeInsight.completion.CompletionContext;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 15.04.2003
 * Time: 17:18:58
 * To change this template use Options | File Templates.
 */
public class FilterGetter implements ContextGetter{
  private ContextGetter myBaseGetter;
  private ElementFilter myFilter;

  public FilterGetter(ContextGetter baseGetter, ElementFilter filter){
    myBaseGetter = baseGetter;
    myFilter = filter;
  }

  public Object[] get(PsiElement context, CompletionContext completionContext){
    final List results = new ArrayList();
    final Object[] elements = myBaseGetter.get(context, null);
    for(int i = 0; i < elements.length; i++){
      final Object element = elements[i];
      if(myFilter.isClassAcceptable(element.getClass()) && myFilter.isAcceptable(element, context)){
        results.add(element);
      }
    }
    return results.toArray();
  }
}
