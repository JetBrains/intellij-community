// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.xml.IDTDElementType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

public class XmlTokenImpl extends LeafPsiElement implements XmlToken, Navigatable, HintedReferenceHost {
  public XmlTokenImpl(@NotNull IElementType type, CharSequence text) {
    super(type, text);
  }

  @Override
  public boolean processElements(PsiElementProcessor processor, PsiElement place) {
    return false;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlToken(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    if(getTokenType() instanceof IDTDElementType){
      return "DTDToken:" + getTokenType().toString();
    }
    return "XmlToken:" + getTokenType().toString();
  }

// Implementation specific

  @NotNull
  @Override
  public IElementType getTokenType() {
    return getElementType();
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return getReferences(PsiReferenceService.Hints.NO_HINTS);
  }

  @Override
  public PsiReference @NotNull [] getReferences(PsiReferenceService.@NotNull Hints hints) {
    final IElementType elementType = getElementType();

    if (elementType == XmlTokenType.XML_DATA_CHARACTERS ||
        elementType == XmlTokenType.XML_CHAR_ENTITY_REF) {
      return ReferenceProvidersRegistry.getReferencesFromProviders(this, hints);
    } else if (elementType == XmlTokenType.XML_NAME && getParent() instanceof PsiErrorElement) {
      final PsiElement element = getPrevSibling();

      if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_END_TAG_START) {
        return new PsiReference[] {TagNameReference.createTagNameReference(this, getNode(), false)};
      }
    }

    return super.getReferences();
  }

  @Override
  public boolean shouldAskParentForReferences(PsiReferenceService.@NotNull Hints hints) {
    return true;
  }

  @Override
  public PsiElement getNavigationElement() {
    if (getTokenType() == XmlTokenType.XML_COMMENT_CHARACTERS) {
      PsiElement parent = this.getParent();
      return parent != null ? parent : this;
    }
    return super.getNavigationElement();
  }

  @Override
  public boolean canNavigate() {
    return getTokenType() == XmlTokenType.XML_NAME && PsiNavigationSupport.getInstance().canNavigate(this) ||
           getTokenType() == XmlTokenType.XML_COMMENT_CHARACTERS; // Custom regions
  }
}
