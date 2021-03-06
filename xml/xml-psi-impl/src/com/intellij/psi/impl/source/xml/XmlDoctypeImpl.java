// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URLReference;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlDoctypeImpl extends XmlElementImpl implements XmlDoctype {
  private static final Logger LOG = Logger.getInstance(XmlDoctypeImpl.class);

  public XmlDoctypeImpl() {
    super(XmlElementType.XML_DOCTYPE);
  }

  @Override
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

  @Override
  @Nullable
  public String getDtdUri() {
    final PsiElement dtdUrlElement = getDtdUrlElement();
    if (dtdUrlElement == null || dtdUrlElement.getTextLength() == 0) return null;
    return extractValue(dtdUrlElement);
  }

  private static String extractValue(PsiElement element) {
    String text = element.getText();

    if (!text.startsWith("\"") && !text.startsWith("'")) {
      if (hasInjectedEscapingQuotes(element, text)) return stripInjectedEscapingQuotes(text);
    }
    return StringUtil.stripQuotesAroundValue(text);
  }

  // TODO: share common code
  private static String stripInjectedEscapingQuotes(String text) {
    return text.substring(2, text.length() - 2);
  }

  private static boolean hasInjectedEscapingQuotes(PsiElement element, String text) {
    if (text.startsWith("\\") && text.length() >= 4) {
      char escapedChar = text.charAt(1);
      PsiElement context =
        InjectedLanguageManager.getInstance(element.getContainingFile().getProject()).getInjectionHost(element.getContainingFile());

      if (context != null &&
          context.textContains(escapedChar) &&
          context.getText().startsWith(String.valueOf(escapedChar)) &&
          text.endsWith("\\"+escapedChar)
        ) {
        return true;
      }
    }
    return false;
  }

  @Override
  @Nullable
  public PsiElement getDtdUrlElement() {
    ASTNode child = getNode().findChildByType(XmlTokenType.XML_DOCTYPE_PUBLIC);
    PsiElement docTypePublic = child != null ? child.getPsi() : null;

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
          return element;
        }
      }
    }

    child = getNode().findChildByType(XmlTokenType.XML_DOCTYPE_SYSTEM);
    PsiElement docTypeSystem = child != null ? child.getPsi() : null;

    if (docTypeSystem != null){
      PsiElement element = docTypeSystem.getNextSibling();

      //element = element.getNextSibling(); // pass qoutes
      while(element instanceof PsiWhiteSpace || element instanceof XmlComment){
        element = element.getNextSibling();
      }

      if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN){
        return element;
      }
    }

    return null;
  }

  @Override
  public XmlElement getNameElement() {
    ASTNode child = getNode().findChildByType(XmlTokenType.XML_NAME);
    return child != null ? child.getPsi(XmlElement.class) : null;
  }

  @Override
  @Nullable
  public String getPublicId() {
    return getSomeId(XmlTokenType.XML_DOCTYPE_PUBLIC);
  }

  @Override
  public String getSystemId() {
    return getSomeId(XmlTokenType.XML_DOCTYPE_SYSTEM);
  }

  private String getSomeId(final IElementType elementType) {
    ASTNode child = getNode().findChildByType(elementType);
    PsiElement docTypeSystem = child != null ? child.getPsi() : null;

    if (docTypeSystem != null) {
      PsiElement element = docTypeSystem.getNextSibling();

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

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlDoctype(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public XmlMarkupDecl getMarkupDecl() {
    for(PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()){
      if (child instanceof XmlMarkupDecl){
        return (XmlMarkupDecl)child;
      }
    }

    return null;
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    final PsiElement dtdUrlElement = getDtdUrlElement();

    PsiReference uriRef = null;
    if (dtdUrlElement != null) {
      uriRef = createUrlReference(dtdUrlElement);
    }

    final PsiReference[] refs = ReferenceProvidersRegistry.getReferencesFromProviders(this);

    return uriRef == null ? refs : ArrayUtil.mergeArrays(new PsiReference[] {uriRef}, refs);
  }

  protected PsiReference createUrlReference(final PsiElement dtdUrlElement) {
    return new URLReference(XmlDoctypeImpl.this) {
      @Override
      @NotNull
      public String getCanonicalText() {
        return extractValue(dtdUrlElement);
      }
      @NotNull
      @Override
      public TextRange getRangeInElement() {
        return TextRange.from(dtdUrlElement.getTextRange().getStartOffset() - getTextRange().getStartOffset() + 1, Math.max(dtdUrlElement.getTextRange().getLength() - 2, 0));
      }
    };
  }
}
