package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;

/**
 * @author spleaner
 */
public class TextContainFilter extends XmlTextFilter {
  public TextContainFilter(String[] values){
    super(values);
  }

  public TextContainFilter(String value1, String value2){
    super(value1, value2);
  }

  public TextContainFilter(String value){
    super(value);
  }

  public TextContainFilter(){}

  public boolean isAcceptable(Object element, PsiElement context){
    if(element != null) {
      for (final String value : myValue) {
        if (value == null) {
          return true;
        }
        String elementValue = getTextByElement(element);
        if (elementValue == null) return false;
        if (elementValue.contains(value)) return true;
      }
    }

    return false;
  }


}
