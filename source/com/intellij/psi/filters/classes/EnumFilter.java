package com.intellij.psi.filters.classes;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import org.jdom.Element;

public class EnumFilter
  implements ElementFilter{

  protected boolean isClassAcceptable(PsiClass aClass){
    return aClass.isEnum();
  }
  public void readExternal(Element element)
    throws InvalidDataException{
  }

  public void writeExternal(Element element)
    throws WriteExternalException{
    throw new WriteExternalException("Filter data could _not_ be written");
  }

  public boolean isClassAcceptable(Class hintClass){
    return PsiClass.class.isAssignableFrom(hintClass);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof PsiClass){
      return ((PsiClass)element).isEnum();
    }
    return false;
  }

  public String toString(){
    return "enum";
  }
}
