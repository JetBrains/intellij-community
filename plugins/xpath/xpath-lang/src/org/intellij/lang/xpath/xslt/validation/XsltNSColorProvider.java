// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath.xslt.validation;

import com.intellij.codeInsight.daemon.impl.analysis.XmlNSColorProvider;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class XsltNSColorProvider implements XmlNSColorProvider {

  @Override
  public @Nullable TextAttributesKey getKeyForNamespace(String namespace, XmlElement context) {
    if (!(context instanceof XmlTag)) return null;
    if (XsltSupport.XSLT_NS.equals(((XmlTag)context).getNamespace())) return XsltSupport.XSLT_DIRECTIVE;
    return null;
  }
}
