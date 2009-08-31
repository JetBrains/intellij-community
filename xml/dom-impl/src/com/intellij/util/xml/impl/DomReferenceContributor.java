/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.patterns.XmlPatterns;

/**
 * @author peter
 */
public class DomReferenceContributor extends PsiReferenceContributor{
  public void registerReferenceProviders(final PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(XmlPatterns.xmlTag(), new GenericValueReferenceProvider());
    registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue(), new GenericValueReferenceProvider());
  }
}
