/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 26, 2002
 * Time: 6:25:08 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.PsiBaseElementProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.util.XmlUtil;

public abstract class XmlElementImpl extends CompositePsiElement implements XmlElement {
  public XmlElementImpl(IElementType type) {
    super(type);
  }

  public boolean processElements(PsiElementProcessor processor, PsiElement place){
    return XmlUtil.processXmlElements(this, processor, false);
  }

  public XmlElement findElementByTokenType(final IElementType type){
    final XmlElement[] result = new XmlElement[1];
    result[0] = null;

    processElements(new PsiBaseElementProcessor(){
      public boolean execute(PsiElement element){
        if(element instanceof TreeElement && ((TreeElement)element).getElementType() == type){
          result[0] = (XmlElement)element;
          return false;
        }
        return true;
      }
    }, this);

    return result[0];
  }

  public PsiElement getContext() {
    final PsiElement data = getUserData(ORIGINAL_ELEMENT);
    if(data != null) return (XmlElement)data;
    return super.getParent();
  }

  public PsiElement getParent(){
    return getContext();
  }

  public TextRange getTextRange() {
    final int textOffset = getStartOffset();
    return new TextRange(textOffset, textOffset + getTextLength());
  }
}
