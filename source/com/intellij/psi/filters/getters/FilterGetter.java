package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.ElementFilter;

import java.util.ArrayList;
import java.util.List;

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
    final Object[] elements = myBaseGetter.get(context, completionContext);
    for (final Object element : elements) {
      if (myFilter.isClassAcceptable(element.getClass()) && myFilter.isAcceptable(element, context)) {
        results.add(element);
      }
    }
    return results.toArray();
  }
}
