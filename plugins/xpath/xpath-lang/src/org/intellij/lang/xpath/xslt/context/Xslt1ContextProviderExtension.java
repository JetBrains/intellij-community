// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.xslt.context;

import com.intellij.lang.Language;
import com.intellij.psi.xml.XmlElement;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.context.ContextProvider;

public class Xslt1ContextProviderExtension extends XsltContextProviderExtensionBase {

  @Override
  protected ContextProvider create(XmlElement xmlElement) {
    return new XsltContextProvider(xmlElement);
  }

  @Override
  protected boolean acceptsLanguage(Language language) {
    return language == XPathFileType.XPATH.getLanguage();
  }
}