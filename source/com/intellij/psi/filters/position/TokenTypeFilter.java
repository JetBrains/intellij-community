package com.intellij.psi.filters.position;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.lang.ASTNode;
import org.jdom.Element;

import java.lang.reflect.Field;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 10.03.2003
 * Time: 12:10:08
 * To change this template use Options | File Templates.
 */
public class TokenTypeFilter implements ElementFilter{
  private IElementType myType = null;

  public TokenTypeFilter(){}

  public TokenTypeFilter(IElementType type){
    myType = type;
  }

  public boolean isClassAcceptable(Class hintClass){
    return PsiDocToken.class.isAssignableFrom(hintClass) || XmlToken.class.isAssignableFrom(hintClass);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof PsiElement) {
      final ASTNode node = ((PsiElement)element).getNode();
      return node != null ? node.getElementType() == myType : false;
    }
    else if(element instanceof ASTNode){
      return ((ASTNode)element).getElementType() == myType;
    }
    return false;
  }

  public void readExternal(Element element) throws InvalidDataException{
    final String typeName = element.getText().trim();
    final Class tokenClass = PsiDocToken.class;

    try{
      final Field field = tokenClass.getField(typeName);
      if(IElementType.class.isAssignableFrom(field.getType())){
        myType = (IElementType)field.get(null);
      }
    }
    catch(NoSuchFieldException e){
    }
    catch(SecurityException e){
    }
    catch(IllegalAccessException e){
    }
  }

  public void writeExternal(Element element)
  throws WriteExternalException{
    throw new WriteExternalException("Filter data could _not_ be written");
  }

  public String toString(){
    return "token-type(" + myType + ")";
  }
}
