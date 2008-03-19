package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.08.2003
 * Time: 18:18:38
 * To change this template use Options | File Templates.
 */
public class TextStartFilter extends XmlTextFilter{
  public TextStartFilter(String[] values){
    super(values);
  }

  public TextStartFilter(String value1, String value2){
    super(value1, value2);
  }

  public TextStartFilter(String value){
    super(value);
  }

  public TextStartFilter(){}

  public boolean isAcceptable(Object element, PsiElement context){
    if(element != null) {
      for (final String value : myValue) {
        if (value == null) {
          return true;
        }
        String elementValue = getTextByElement(element);
        if (elementValue == null) return false;
        if (elementValue.startsWith(value)) return true;
      }
    }

    return false;
  }
}
