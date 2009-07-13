/*
 * @author max
 */
package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;

public class XmlTagFilter implements ElementFilter {
  public static final XmlTagFilter INSTANCE = new XmlTagFilter();

  private XmlTagFilter() {}

  public boolean isAcceptable(Object element, PsiElement context) {
    return element instanceof XmlTag;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }
}
