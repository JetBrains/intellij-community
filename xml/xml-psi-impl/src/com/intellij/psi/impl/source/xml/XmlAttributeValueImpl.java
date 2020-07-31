// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.model.psi.PsiSymbolReference;
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
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.regexp.DefaultRegExpPropertiesProvider;
import org.intellij.lang.regexp.RegExpLanguageHost;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpNamedGroupRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;

public class XmlAttributeValueImpl extends XmlElementImpl
  implements XmlAttributeValue, PsiLanguageInjectionHost, RegExpLanguageHost, PsiMetaOwner, PsiMetaData, HintedReferenceHost {
  private static final Logger LOG = Logger.getInstance(XmlAttributeValueImpl.class);

  public XmlAttributeValueImpl() {
    super(XmlElementType.XML_ATTRIBUTE_VALUE);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlAttributeValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NotNull
  @Override
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

  @Override
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

  @Override
  public @NotNull Iterable<? extends @NotNull PsiSymbolReference> getOwnReferences() {
    PsiReference[] references = getReferences();
    return references.length == 0 ? Collections.emptyList() : Arrays.asList(references);
  }

  @Override
  public PsiReference @NotNull [] getReferences(PsiReferenceService.@NotNull Hints hints) {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, hints);
  }

  @Override
  public boolean shouldAskParentForReferences(PsiReferenceService.@NotNull Hints hints) {
    return false;
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return getReferences(PsiReferenceService.Hints.NO_HINTS);
  }

  @Override
  public PsiReference getReference() {
    final PsiReference[] refs = getReferences();
    if (refs.length > 0) return refs[0];
    return null;
  }


  @Override
  public int getTextOffset() {
    return getTextRange().getStartOffset() + 1;
  }

  @Override
  public boolean isValidHost() {
    return getParent() instanceof XmlAttribute;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    try {
      final String quoteChar = getTextLength() > 0 ? getText().substring(0, 1) : "";
      String contents = StringUtil.containsAnyChar(quoteChar, "'\"") ?
              StringUtil.trimEnd(StringUtil.trimStart(text, quoteChar), quoteChar) : text;
      XmlAttribute newAttribute = XmlElementFactory.getInstance(getProject()).createAttribute(
        StringUtil.defaultIfEmpty((getParent() instanceof XmlAttribute) ? ((XmlAttribute)getParent()).getName() : null, "q"),
        contents, this);
      XmlAttributeValue newValue = newAttribute.getValueElement();

      CheckUtil.checkWritable(this);
      replaceAllChildrenToChildrenOf(newValue.getNode());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return this;
  }

  @Override
  @NotNull
  public LiteralTextEscaper<XmlAttributeValueImpl> createLiteralTextEscaper() {
    return new XmlAttributeLiteralEscaper(this);
  }

  @Override
  public PsiMetaData getMetaData() {
    return this;
  }

  @Override
  public PsiElement getDeclaration() {
    return this;
  }

  @Override
  public String getName(final PsiElement context) {
    return getValue();
  }

  @Override
  public String getName() {
    return getValue();
  }

  @Override
  public void init(final PsiElement element) {
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

  @Override
  public boolean characterNeedsEscaping(char c) {
    return c == ']' || c == '}';
  }

  @Override
  public boolean supportsPerl5EmbeddedComments() {
    return false;
  }

  @Override
  public boolean supportsPossessiveQuantifiers() {
    return true;
  }

  @Override
  public boolean supportsPythonConditionalRefs() {
    return false;
  }

  @Override
  public boolean supportsNamedGroupSyntax(RegExpGroup group) {
    return true;
  }

  @Override
  public boolean supportsNamedGroupRefSyntax(RegExpNamedGroupRef ref) {
    return true;
  }

  @Override
  public boolean supportsExtendedHexCharacter(RegExpChar regExpChar) {
    return false;
  }

  @Override
  public boolean isValidCategory(@NotNull String category) {
    if (category.startsWith("Is")) {
      try {
        return Character.UnicodeBlock.forName(category.substring(2)) != null;
      }
      catch (IllegalArgumentException ignore) {}
    }
    for (String[] name : DefaultRegExpPropertiesProvider.getInstance().getAllKnownProperties()) {
      if (name[0].equals(category)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String[] @NotNull [] getAllKnownProperties() {
    return DefaultRegExpPropertiesProvider.getInstance().getAllKnownProperties();
  }

  @Nullable
  @Override
  public String getPropertyDescription(@Nullable String name) {
    return DefaultRegExpPropertiesProvider.getInstance().getPropertyDescription(name);
  }

  @Override
  public String[] @NotNull [] getKnownCharacterClasses() {
    return DefaultRegExpPropertiesProvider.getInstance().getKnownCharacterClasses();
  }
}
