package com.intellij.psi.filters.position;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.ReflectionCache;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 10.03.2003
 * Time: 12:10:08
 * To change this template use Options | File Templates.
 */
public class XmlTokenTypeFilter implements ElementFilter{
  private IElementType myType = null;

  public XmlTokenTypeFilter(){}

  public XmlTokenTypeFilter(IElementType type){
    myType = type;
  }

  public boolean isClassAcceptable(Class hintClass){
    return ReflectionCache.isAssignable(XmlToken.class, hintClass);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof PsiElement) {
      final ASTNode node = ((PsiElement)element).getNode();
      return node != null && node.getElementType() == myType;
    }
    else if(element instanceof ASTNode){
      return ((ASTNode)element).getElementType() == myType;
    }
    return false;
  }

  public String toString(){
    return "token-type(" + myType + ")";
  }
}