package com.intellij.psi.impl.source.xml;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.xml.XmlDecl;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class XmlDeclImpl extends XmlElementImpl implements XmlDecl{
  public XmlDeclImpl() {
    super(XML_DECL);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitXmlDecl(this);
  }
}
