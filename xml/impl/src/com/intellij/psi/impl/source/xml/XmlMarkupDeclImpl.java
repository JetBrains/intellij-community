package com.intellij.psi.impl.source.xml;

import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlMarkupDecl;

/**
 * @author Mike
 */
public class XmlMarkupDeclImpl extends XmlElementImpl implements XmlMarkupDecl {
  public XmlMarkupDeclImpl() {
    super(XmlElementType.XML_MARKUP_DECL);
  }

  public PsiMetaData getMetaData(){
    return MetaRegistry.getMeta(this);
  }

  public boolean isMetaEnough(){
    return true;  //To change body of implemented methods use Options | File Templates.
  }
}
