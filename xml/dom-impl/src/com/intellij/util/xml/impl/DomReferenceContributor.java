// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.annotations.NotNull;

public class DomReferenceContributor extends PsiReferenceContributor{
  @Override
  public void registerReferenceProviders(final @NotNull PsiReferenceRegistrar registrar) {
    GenericValueReferenceProvider provider = new GenericValueReferenceProvider();

    registrar.registerReferenceProvider(XmlPatterns.xmlTag(), provider);
    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue(), provider);
  }
}
