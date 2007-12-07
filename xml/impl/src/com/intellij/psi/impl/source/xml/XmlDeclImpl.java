package com.intellij.psi.impl.source.xml;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlDecl;
import com.intellij.psi.xml.XmlElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class XmlDeclImpl extends XmlElementImpl implements XmlDecl{
  public XmlDeclImpl() {
    super(XmlElementType.XML_DECL);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlDecl(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}
