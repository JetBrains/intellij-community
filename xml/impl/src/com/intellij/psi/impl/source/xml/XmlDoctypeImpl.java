package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URIReferenceProvider;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mike
 */
public class XmlDoctypeImpl extends XmlElementImpl implements XmlDoctype, XmlElementType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlDoctypeImpl");

  public XmlDoctypeImpl() {
    super(XmlElementType.XML_DOCTYPE);
  }

  public void clearCaches() {
    final XmlDocument doc = getContainingDocument();
    if (doc != null) {
      final XmlTag rootTag = doc.getRootTag();
      if (rootTag instanceof TreeElement) {
        ((TreeElement)rootTag).clearCaches();
      }
    }
    super.clearCaches();
  }

  private XmlDocument getContainingDocument() {
    for (PsiElement elem = getParent(); elem != null; elem = elem.getParent()) {
      if (elem instanceof XmlDocument) {
        return (XmlDocument)elem;
      }
      if (elem instanceof PsiFile) {
        break; // optimization
      }
    }
    return null;
  }
  
  public int getChildRole(ASTNode child) {
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

  @Nullable
  public String getDtdUri() {
    final XmlElement dtdUrlElement = getDtdUrlElement();
    if (dtdUrlElement == null || dtdUrlElement.getTextLength() == 0) return null;
    return extractValue(dtdUrlElement);
  }

  private String extractValue(PsiElement element) {
    return StringUtil.stripQuotesAroundValue(element.getText());
  }

  @Nullable
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

  @Nullable
  public String getPublicId() {
    PsiElement docTypePublic = findChildByRoleAsPsiElement(ChildRole.XML_DOCTYPE_PUBLIC);

    if (docTypePublic != null) {
      PsiElement element = docTypePublic.getNextSibling();

      while (element instanceof PsiWhiteSpace || element instanceof XmlComment) {
        element = element.getNextSibling();
      }

      //element = element.getNextSibling(); // pass qoutes
      if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
        if (element.getTextLength() != 0) {
          return extractValue(element);
        }
      }
    }
    return null;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitXmlDoctype(this);
  }

  public XmlMarkupDecl getMarkupDecl() {
    for(PsiElement child = this.getFirstChild(); child != null; child = child.getNextSibling()){
      if (child instanceof XmlMarkupDecl){
        return (XmlMarkupDecl)child;
      }
    }

    return null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    final XmlElement dtdUrlElement = getDtdUrlElement();
    final PsiElement docTypePublic = findChildByRoleAsPsiElement(ChildRole.XML_DOCTYPE_PUBLIC);
    PsiReference uriRefs[] = null;

    if (dtdUrlElement != null) {
      uriRefs = new PsiReference[1];
      uriRefs[0] = new URIReferenceProvider.URLReference(XmlDoctypeImpl.this) {
        public Object[] getVariants() {
          return (docTypePublic != null)?
                 super.getVariants(): PsiReference.EMPTY_ARRAY;
        }
        public String getCanonicalText() {
          return extractValue(dtdUrlElement);
        }
        public TextRange getRangeInElement() {
          return new TextRange(
            dtdUrlElement.getTextRange().getStartOffset() - getTextRange().getStartOffset() + 1,
            dtdUrlElement.getTextRange().getEndOffset() - getTextRange().getStartOffset() - 1
          );
        }
      };
    }

    final PsiReference[] referencesFromProviders = ResolveUtil.getReferencesFromProviders(this,XmlDoctype.class);

    return ArrayUtil.mergeArrays(
      uriRefs != null? uriRefs: PsiReference.EMPTY_ARRAY,
      referencesFromProviders,
      PsiReference.class
    );
  }
}
