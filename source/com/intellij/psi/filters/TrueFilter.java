package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 03.02.2003
 * Time: 17:31:05
 * To change this template use Options | File Templates.
 */
public class TrueFilter implements ElementFilter{

  public boolean isClassAcceptable(Class hintClass){
    return true;
  }

  public boolean isAcceptable(Object element, PsiElement context){
    return true;
  }

  public String toString(){
    return "true";
  }

  public static final ElementFilter INSTANCE = new TrueFilter();

  private TrueFilter() {}
}
