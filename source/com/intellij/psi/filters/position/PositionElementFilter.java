package com.intellij.psi.filters.position;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterUtil;
import org.jdom.Element;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 13:51:02
 * To change this template use Options | File Templates.
 */
public abstract class PositionElementFilter
  implements ElementFilter{
  private ElementFilter myFilter;

  public void setFilter(ElementFilter filter){
    myFilter = filter;
  }

  public ElementFilter getFilter(){
    return myFilter;
  }

  public void readExternal(Element element)
    throws InvalidDataException{
    myFilter = (ElementFilter)FilterUtil.readFilterGroup(element).get(0);
  }

  public void writeExternal(Element element)
    throws WriteExternalException{
    throw new WriteExternalException("Filter data could _not_ be written");
  }

  public boolean isClassAcceptable(Class hintClass){
    return true;
  }

  protected static final PsiElement getOwnerChild(final PsiElement scope, PsiElement element){
    while(element != null && element.getParent() != scope){
      element = element.getParent();
    }
    return element;
  }
}
