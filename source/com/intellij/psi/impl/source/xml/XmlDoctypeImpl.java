package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;

/**
 * @author Mike
 */
public class XmlDoctypeImpl extends XmlElementImpl implements XmlDoctype {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlDoctypeImpl");

  public XmlDoctypeImpl() {
    super(XML_DOCTYPE);
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == XML_DOCTYPE_PUBLIC) {
      return ChildRole.XML_DOCTYPE_PUBLIC;
    }
    else if (i == XML_DOCTYPE_SYSTEM) {
      return ChildRole.XML_DOCTYPE_SYSTEM;
    }
    else if (i == XML_NAME) {
      return ChildRole.XML_NAME;
    }
    else {
      return ChildRole.NONE;
    }
  }


  public String getDtdUri() {
    final XmlElement dtdUrlElement = getDtdUrlElement();
    if (dtdUrlElement == null) return null;
    return extractValue(dtdUrlElement);
  }

  private String extractValue(PsiElement element) {
    String text = element.getText();
    return text.substring(1, text.length() - 1);
  }

  public XmlElement getDtdUrlElement() {
    PsiElement docTypePublic = findChildByRoleAsPsiElement(ChildRole.XML_DOCTYPE_PUBLIC);

    if (docTypePublic != null){
      PsiElement element = docTypePublic.getNextSibling();

      while(element instanceof PsiWhiteSpace || element instanceof XmlComment){
        element = element.getNextSibling();
      }

      //element = element.getNextSibling(); // pass qoutes
      if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN){
        element = element.getNextSibling();

        while(element instanceof PsiWhiteSpace || element instanceof XmlComment){
          element = element.getNextSibling();
        }

        if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN){
          return (XmlElement)element;
        }
      }
    }

    PsiElement docTypeSystem = findChildByRoleAsPsiElement(ChildRole.XML_DOCTYPE_SYSTEM);

    if (docTypeSystem != null){
      PsiElement element = docTypeSystem.getNextSibling();

      //element = element.getNextSibling(); // pass qoutes
      while(element instanceof PsiWhiteSpace || element instanceof XmlComment){
        element = element.getNextSibling();
      }

      if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN){
        return (XmlElement)element;
      }
    }

    return null;
  }

  public XmlElement getNameElement() {
    return (XmlElement)findChildByRoleAsPsiElement(ChildRole.XML_NAME);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlDoctype(this);
  }

  public XmlMarkupDecl getMarkupDecl() {
    for(PsiElement child = this.getFirstChild(); child != null; child = child.getNextSibling()){
      if (child instanceof XmlMarkupDecl){
        XmlMarkupDecl decl = (XmlMarkupDecl)child;
        return decl;
      }
    }

    return null;
  }

  public PsiReference getReference() {
    final XmlElement dtdUrlElement = getDtdUrlElement();
    if (dtdUrlElement == null) return null;

    final String uri = extractValue(dtdUrlElement);

    return new PsiReference() {
      public PsiElement getElement() {
        return XmlDoctypeImpl.this;
      }

      public TextRange getRangeInElement() {
        return new TextRange(
          dtdUrlElement.getTextRange().getStartOffset() - getTextRange().getStartOffset(),
          dtdUrlElement.getTextRange().getEndOffset() - getTextRange().getStartOffset()
        );
      }

      public PsiElement resolve() {
        return XmlUtil.findXmlFile(XmlUtil.getContainingFile(XmlDoctypeImpl.this), uri);
      }

      public String getCanonicalText() {
        return uri;
      }

      public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        throw new IncorrectOperationException();
      }

      public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
        throw new IncorrectOperationException();
      }

      public boolean isReferenceTo(PsiElement element) {
        return element == dtdUrlElement;
      }

      public Object[] getVariants() {
        return null;
      }

      public boolean isSoft() {
        return false;
      }
    };
  }
}
