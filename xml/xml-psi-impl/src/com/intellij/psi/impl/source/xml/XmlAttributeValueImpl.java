/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationWithSeparator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.injected.XmlAttributeLiteralEscaper;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Mike
 */
public class XmlAttributeValueImpl extends XmlElementImpl implements XmlAttributeValue, PsiLanguageInjectionHost, PsiMetaOwner, PsiMetaData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlAttributeValueImpl");
  private volatile PsiReference[] myCachedReferences;
  private volatile long myModCount;

  public XmlAttributeValueImpl() {
    super(XmlElementType.XML_ATTRIBUTE_VALUE);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlAttributeValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String getValue() {
    // it is more correct way to strip quotes since injected xml may have quotes encoded
    String text = getText();
    ASTNode startQuote = findChildByType(XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER);
    if (startQuote != null) {
      text = StringUtil.trimStart(text, startQuote.getText());
    }
    ASTNode endQuote = findChildByType(XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER);
    if (endQuote != null) {
      text = StringUtil.trimEnd(text, endQuote.getText());
    }
    return text;
  }

  public TextRange getValueTextRange() {
    final TextRange range = getTextRange();
    final String value = getValue();
    if (value.isEmpty()) {
      return range; 
    }
    final int start = range.getStartOffset() + getText().indexOf(value);
    final int end = start + value.length();
    return new TextRange(start, end);
  }

  public void clearCaches() {
    super.clearCaches();
    myCachedReferences = null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    PsiReference[] cachedReferences = myCachedReferences;
    final long curModCount = getManager().getModificationTracker().getModificationCount();
    if (cachedReferences != null && myModCount == curModCount) {
      return cachedReferences;
    }
    cachedReferences = ReferenceProvidersRegistry.getReferencesFromProviders(this, XmlAttributeValue.class);
    myCachedReferences = cachedReferences;
    myModCount = curModCount;
    return cachedReferences;
  }

  public PsiReference getReference() {
    final PsiReference[] refs = getReferences();
    if (refs.length > 0) return refs[0];
    return null;
  }


  public int getTextOffset() {
    return getTextRange().getStartOffset() + 1;
  }

  @Override
  public boolean isValidHost() {
    return getParent() instanceof XmlAttributeImpl;
  }

  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    try {
      final String quoteChar = getTextLength() > 0 ? getText().substring(0, 1) : "";
      String contents = StringUtil.containsAnyChar(quoteChar, "'\"") ?
              StringUtil.trimEnd(StringUtil.trimStart(text, quoteChar), quoteChar) : text;
      XmlAttribute newAttribute = XmlElementFactory.getInstance(getProject()).createXmlAttribute("q", contents);
      XmlAttributeValue newValue = newAttribute.getValueElement();

      CheckUtil.checkWritable(this);
      replaceAllChildrenToChildrenOf(newValue.getNode());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return this;
  }

  @NotNull
  public LiteralTextEscaper<XmlAttributeValueImpl> createLiteralTextEscaper() {
    return new XmlAttributeLiteralEscaper(this);
  }

  public PsiMetaData getMetaData() {
    return this;
  }

  public PsiElement getDeclaration() {
    return this;
  }

  public String getName(final PsiElement context) {
    return getValue();
  }

  public String getName() {
    return getValue();
  }

  public void init(final PsiElement element) {
  }

  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentationWithSeparator() {
      @Override
      public String getPresentableText() {
        return getText();
      }

      @Override
      public String getLocationString() {
        return SymbolPresentationUtil.getFilePathPresentation(getContainingFile());
      }

      @Override
      public Icon getIcon(boolean open) {
        return null;
      }
    };
  }
}
