// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.impl.source.xml.behavior.CDATAOnAnyEncodedPolicy;


public final class LanguageXmlPsiPolicy extends LanguageExtension<XmlPsiPolicy> {
  public static final LanguageXmlPsiPolicy INSTANCE = new LanguageXmlPsiPolicy();

  private LanguageXmlPsiPolicy() {
    super("com.intellij.xml.psiPolicy", new CDATAOnAnyEncodedPolicy());
  }
}
