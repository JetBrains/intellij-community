package com.intellij.psi.filters;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import org.jdom.Element;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 03.02.2003
 * Time: 17:31:05
 * To change this template use Options | File Templates.
 */
public class FalseFilter
 implements ElementFilter{

  public boolean isClassAcceptable(Class hintClass){
    return true;
  }

  public boolean isAcceptable(Object element, PsiElement context){
    return false;
  }

  public void readExternal(Element element)
    throws InvalidDataException{
  }

  public void writeExternal(Element element)
    throws WriteExternalException{
    throw new WriteExternalException("Filter data could _not_ be written");
  }

  public String toString(){
    return "false";
  }
}
