package com.intellij.psi.filters.classes;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import org.jdom.Element;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 21:00:45
 * To change this template use Options | File Templates.
 */
public class AnnotationTypeFilter
  implements ElementFilter{

  protected boolean isClassAcceptable(PsiClass aClass){
    return aClass.isAnnotationType();
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
      return ((PsiClass)element).isAnnotationType();
    }
    return false;
  }

  public String toString(){
    return "annotationType";
  }
}
