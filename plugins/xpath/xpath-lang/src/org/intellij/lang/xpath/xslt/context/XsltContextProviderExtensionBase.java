// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath.xslt.context;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import org.intellij.lang.xpath.XPathFile;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.ContextProviderExtension;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.NotNull;

public abstract class XsltContextProviderExtensionBase extends ContextProviderExtension {
  @Override
  public boolean accepts(XPathFile file) {
    final PsiElement context = file.getContext();
    if (!(context instanceof XmlElement)) return false;
    final XmlAttribute att = PsiTreeUtil.getParentOfType(context, XmlAttribute.class);
    if (att == null) return false;
    return XsltSupport.isXPathAttribute(att) ? acceptsLanguage(file.getLanguage()) : false;
  }

  protected abstract boolean acceptsLanguage(Language language);

  @Override
  public @NotNull ContextProvider getContextProvider(XPathFile file) {
    final XmlElement xmlElement = (XmlElement)file.getContext();
    assert xmlElement != null;
    return create(xmlElement);
  }

  protected abstract ContextProvider create(XmlElement xmlElement);
}