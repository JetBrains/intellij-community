package com.intellij.psi.filters.classes;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.util.InheritanceUtil;
import org.jdom.Element;

import java.lang.ref.SoftReference;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 04.02.2003
 * Time: 12:40:51
 * To change this template use Options | File Templates.
 */
public class AssignableFromContextFilter
 implements ElementFilter{
  public AssignableFromContextFilter(){}

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

  SoftReference myCurrentContext = new SoftReference(null);
  SoftReference myCachedClass = new SoftReference(null);
  public boolean isAcceptable(Object element, PsiElement context){
    if(myCurrentContext.get() != context){
      myCurrentContext = new SoftReference(context);
      PsiElement cachedClass = context;
      while(cachedClass != null && !(cachedClass instanceof PsiClass))
        cachedClass = cachedClass.getContext();
      myCachedClass = new SoftReference(cachedClass);
    }


    if(myCachedClass.get() instanceof PsiClass && element instanceof PsiClass){
      final String qualifiedName = ((PsiClass)myCachedClass.get()).getQualifiedName();
      return qualifiedName != null && (qualifiedName.equals(((PsiClass)element).getQualifiedName())
        || ((PsiClass)element).isInheritor((PsiClass)myCachedClass.get(), true));

    }
    return false;
  }

  public String toString(){
    return "assignable-from-context";
  }
}


