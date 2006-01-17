package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlConditionalSection;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlEntityRef;
import com.intellij.openapi.util.text.StringUtil;

/**
 * Created by IntelliJ IDEA.
 * User: maxim.mossienko
 * Date: Jan 13, 2006
 * Time: 6:55:25 PM
 * To change this template use File | Settings | File Templates.
 */
public class XmlConditionalSectionImpl extends XmlElementImpl implements XmlConditionalSection {
  public XmlConditionalSectionImpl() {
    super(XML_CONDITIONAL_SECTION);
  }

  public boolean isIncluded(PsiFile targetFile) {
    ASTNode child = findChildByType(XML_CONDITIONAL_SECTION_START);

    if (child != null) {
      child = child.getTreeNext();

      if (child != null && child.getElementType() == WHITE_SPACE) {
        child = child.getTreeNext();
      }

      if (child != null) {
        IElementType elementType = child.getElementType();
        if (elementType == XML_CONDITIONAL_INCLUDE) return true;
        if (elementType == XML_CONDITIONAL_IGNORE) return false;

        if (elementType == XML_ENTITY_REF) {
          XmlEntityRef xmlEntityRef = (XmlEntityRef)child.getPsi();

          final String text = xmlEntityRef.getText();
          String name = text.substring(1,text.length() - 1);

          PsiElement psiElement = targetFile != null ? XmlEntityRefImpl.getCachedEntity( targetFile, name): null;

          if (psiElement instanceof XmlEntityDecl) {
            final XmlEntityDecl decl = (XmlEntityDecl)psiElement;
            
            if(decl.isInternalReference()) {
              for (ASTNode e = decl.getNode().getFirstChildNode(); e != null; e = e.getTreeNext()) {
                if (e.getElementType() == ElementType.XML_ATTRIBUTE_VALUE) {
                  return StringUtil.stripQuotesAroundValue(e.getText()).equals("INCLUDE");
                }
              }
            }
          }
        }
      }
    }
    return false;
  }

  public PsiElement getBodyStart() {
    ASTNode child = findChildByType(XML_MARKUP_START);
    if (child != null) child = child.getTreeNext();
    if (child != null) return child.getPsi();
    return null;
  }
}
