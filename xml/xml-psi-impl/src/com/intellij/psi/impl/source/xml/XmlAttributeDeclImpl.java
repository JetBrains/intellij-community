// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class XmlAttributeDeclImpl extends XmlElementImpl implements XmlAttributeDecl, XmlElementType {
  private static final Logger LOG = Logger.getInstance(XmlAttributeDeclImpl.class);
  @NonNls private static final String ID_ATT = "ID";
  @NonNls private static final String IDREF_ATT = "IDREF";

  public XmlAttributeDeclImpl() {
    super(XML_ATTRIBUTE_DECL);
  }

  @Override
  public XmlElement getNameElement() {
    return findElementByTokenType(XML_NAME);
  }

  @Override
  public boolean isAttributeRequired() {
    return findElementByTokenType(XML_ATT_REQUIRED) != null;
  }

  @Override
  public boolean isAttributeFixed() {
    return findElementByTokenType(XML_ATT_FIXED) != null;
  }

  @Override
  public boolean isAttributeImplied() {
    return findElementByTokenType(XML_ATT_IMPLIED) != null;
  }

  @Override
  public XmlAttributeValue getDefaultValue() {
    return (XmlAttributeValue)findElementByTokenType(XML_ATTRIBUTE_VALUE);
  }

  @Override
  public String getDefaultValueText() {
    XmlAttributeValue value = getDefaultValue();
    if (value == null) return null;
    String text = value.getText();
    if (text.indexOf('%') == -1 && text.indexOf('&') == -1) return text;

    final StringBuilder builder = new StringBuilder();
    value.processElements(new PsiElementProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element) {
        builder.append(element.getText());
        return true;
      }
    }, null);
    return builder.toString();
  }

  @Override
  public boolean isEnumerated() {
    return findElementByTokenType(XML_ENUMERATED_TYPE) != null;
  }

  @Override
  public XmlElement[] getEnumeratedValues() {
    XmlEnumeratedType enumeratedType = (XmlEnumeratedType)findElementByTokenType(XML_ENUMERATED_TYPE);
    if (enumeratedType != null) {
      return enumeratedType.getEnumeratedValues();
    }
    else {
      return XmlElement.EMPTY_ARRAY;
    }
  }

  @Override
  public boolean isIdAttribute() {
    final PsiElement elementType = findElementType();

    return elementType != null && elementType.getText().equals(ID_ATT);
  }

  private PsiElement findElementType() {
    final PsiElement elementName = findElementByTokenType(XML_NAME);
    final PsiElement nextSibling = (elementName != null) ? elementName.getNextSibling() : null;
    final PsiElement elementType = (nextSibling instanceof PsiWhiteSpace) ? nextSibling.getNextSibling() : nextSibling;

    return elementType;
  }

  @Override
  public boolean isIdRefAttribute() {
    final PsiElement elementType = findElementType();

    return elementType != null && elementType.getText().equals(IDREF_ATT);
  }

  @Override
  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    XmlElementChangeUtil.doNameReplacement(this, getNameElement(), name);
    return null;
  }

  @Override
  public String getName() {
    XmlElement name = getNameElement();
    return (name != null) ? name.getText() : null;
  }

  @Override
  public boolean canNavigate() {
    if (isPhysical()) return super.canNavigate();
    final PsiNamedElement psiNamedElement = XmlUtil.findRealNamedElement(this);
    return psiNamedElement != null && psiNamedElement != this && ((Navigatable)psiNamedElement).canNavigate();
  }

  @Override
  public void navigate(final boolean requestFocus) {
    if (isPhysical()) {
      super.navigate(requestFocus);
      return;
    }
    final PsiNamedElement psiNamedElement = XmlUtil.findRealNamedElement(this);
    Navigatable navigatable = PsiNavigationSupport.getInstance().getDescriptor(psiNamedElement);

    if (psiNamedElement instanceof XmlEntityDecl) {
      navigatable = PsiNavigationSupport.getInstance().createNavigatable(
        psiNamedElement.getProject(),
        psiNamedElement.getContainingFile().getVirtualFile(),
        psiNamedElement.getTextRange().getStartOffset() + psiNamedElement.getText().indexOf(getName())
      );
    }
    if (navigatable != null) {
      navigatable.navigate(requestFocus);
    }
  }

  @Override
  @NotNull
  public PsiElement getNavigationElement() {
    return this;
  }
}
