// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.patterns.XmlAttributeValuePattern;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class XsdRegExpInjector implements MultiHostInjector {
  private static class Holder {
    private static final XmlAttributeValuePattern PATTERN =
      XmlPatterns.xmlAttributeValue("value").withSuperParent(2, XmlPatterns.xmlTag().withLocalName("pattern").withNamespace(
        XmlUtil.SCHEMA_URIS));
  }
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {

    if (Holder.PATTERN.accepts(context)) {
      registrar.startInjecting(XsdRegExpParserDefinition.LANGUAGE).
        addPlace(null, null, (PsiLanguageInjectionHost)context, ElementManipulators.getValueTextRange(context)).
        doneInjecting();
    }
  }

  @Override
  public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(XmlAttributeValue.class);
  }
}
