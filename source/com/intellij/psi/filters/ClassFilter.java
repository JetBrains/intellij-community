package com.intellij.psi.filters;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 19:30:55
 * To change this template use Options | File Templates.
 */
public class ClassFilter implements ElementFilter{
  private Class myFilter;
  private boolean myAcceptableFlag = true;

  public ClassFilter(Class filter){
    myFilter = filter;
  }

  public ClassFilter(Class filter, boolean acceptableFlag){
    myFilter = filter;
    myAcceptableFlag = acceptableFlag;
  }


  public void setClassFilter(Class filter){
    myFilter = filter;
  }

  public Class getClassFilter(){
    return myFilter;
  }

  public boolean isClassAcceptable(Class hintClass){
    return myAcceptableFlag ? myFilter.isAssignableFrom(hintClass) : !myFilter.isAssignableFrom(hintClass);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element == null){
      return false;
    }
    return myAcceptableFlag ? myFilter.isAssignableFrom(element.getClass()) : !myFilter.isAssignableFrom(element.getClass());
  }

  public void readExternal(Element element)
    throws InvalidDataException{
    final String className = element.getTextTrim();
    try{
      myFilter = Class.forName(className);
    }
    catch(Exception e){
      throw new InvalidDataException("Invalid class name in class filter");
    }
  }

  public void writeExternal(Element element)
    throws WriteExternalException{
    throw new WriteExternalException("Filter data could _not_ be written");
  }

  public @NonNls String toString(){
    return "class(" + myFilter.getName() + ")";
  }
}

