// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.completion.XmlCompletionContributor.hasEnumerationReference;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.XmlPatterns.xmlAttribute;
import static com.intellij.patterns.XmlPatterns.xmlTag;
import static com.intellij.psi.filters.getters.XmlAttributeValueGetter.getEnumeratedValues;


public class XmlNonFirstCompletionContributor extends CompletionContributor {
  public XmlNonFirstCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().inside(xmlAttribute()), new XmlAttributeReferenceCompletionProvider());
    extend(CompletionType.BASIC, psiElement().inside(xmlTag()), new TagNameReferenceCompletionProvider());
    extend(CompletionType.BASIC, psiElement().inside(XmlPatterns.xmlAttributeValue()), new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        if (position.getNode().getElementType() != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) return;
        XmlAttribute attr = PsiTreeUtil.getParentOfType(position, XmlAttribute.class);
        if (attr != null && !hasEnumerationReference(parameters, result)) {
          final XmlAttributeDescriptor descriptor = attr.getDescriptor();

          if (descriptor != null) {
            if (descriptor.isFixed() && descriptor.getDefaultValue() != null) {
              result.addElement(LookupElementBuilder.create(descriptor.getDefaultValue()));
              return;
            }
            for (String value : getEnumeratedValues(attr)) {
              result.addElement(LookupElementBuilder.create(value));
            }
          }
        }
      }
    });

  }
}
