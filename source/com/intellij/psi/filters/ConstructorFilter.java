package com.intellij.psi.filters;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jdom.Element;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 21:22:31
 * To change this template use Options | File Templates.
 */
public class ConstructorFilter extends ClassFilter {
  public ConstructorFilter(){
    super(PsiMethod.class);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof PsiMethod){
      return ((PsiMethod)element).isConstructor();
    }
    return false;
  }
  public String toString(){
    return "constructor";
  }
}
