package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.openapi.util.JDOMExternalizable;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 19:10:27
 * To change this template use Options | File Templates.
 */
public interface ElementFilter{
  boolean isAcceptable(Object element, PsiElement context);
  boolean isClassAcceptable(Class hintClass);
}
