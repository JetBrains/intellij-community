package com.intellij.psi.filters.element;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.filters.ElementFilter;
import org.jdom.Element;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 14:37:06
 * To change this template use Options | File Templates.
 */
public class PackageEqualsFilter
  implements ElementFilter{

  public boolean isClassAcceptable(Class hintClass){
    return PsiClass.class.isAssignableFrom(hintClass)
      || PsiPackage.class.isAssignableFrom(hintClass);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if (!(element instanceof PsiElement)) return false;
    final String elementPackName = getPackageName((PsiElement) element);
    final String contextPackName = getPackageName(context);
    if(elementPackName != null && contextPackName != null){
      return elementPackName.equals(contextPackName);
    }
    return false;
  }

  protected String getPackageName(PsiElement element){
    if(element instanceof PsiPackage){
      return ((PsiPackage)element).getQualifiedName();
    }
    if(element.getContainingFile() instanceof PsiJavaFile){
      return ((PsiJavaFile)element.getContainingFile()).getPackageName();
    }
    return null;
  }


  public void readExternal(Element element)
    throws InvalidDataException{
  }

  public void writeExternal(Element element)
    throws WriteExternalException{
    throw new WriteExternalException("Filter data could _not_ be written");
  }


  public String toString(){
    return "same-package";
  }
}
