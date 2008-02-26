package com.intellij.psi.filters.position;

import com.intellij.psi.filters.TextFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.util.ReflectionCache;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 07.05.2003
 * Time: 4:13:57
 * To change this template use Options | File Templates.
 */
public class TargetNamespaceFilter extends TextFilter{
  public TargetNamespaceFilter(String str){
    super(str);
  }

  public TargetNamespaceFilter(String[] strs){
    super(strs);
  }

  public boolean isClassAcceptable(Class hintClass){
    return ReflectionCache.isAssignable(XmlTag.class, hintClass) || ReflectionCache.isAssignable(XmlDocument.class, hintClass);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof XmlTag){
      final String attributeValue = ((XmlTag)element).getAttributeValue("targetNamespace");
      if(attributeValue != null){
        for (String aMyValue : myValue) {
          if (aMyValue.equals(attributeValue)) return true;
        }
      }
    }
    else if(element instanceof XmlDocument){
      return isAcceptable(((XmlDocument) element).getRootTag(), context);
    }
    return false;
  }
}
