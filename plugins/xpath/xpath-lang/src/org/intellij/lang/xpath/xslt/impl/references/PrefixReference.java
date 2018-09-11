// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.xslt.impl.references;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import org.intellij.lang.xpath.xslt.context.XsltNamespaceContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrefixReference extends SimpleAttributeReference implements EmptyResolveMessageProvider {

  private final TextRange myRange;

  public PrefixReference(XmlAttribute attribute) {
    super(attribute);
    myRange = getPrefixRange(myAttribute);
  }

  public PrefixReference(XmlAttribute attribute, TextRange range) {
    super(attribute);
    myRange = range;
  }

  public static TextRange getPrefixRange(XmlAttribute attribute) {
    final String value = attribute.getValue();
    final int p = value.indexOf(':');
    if (p == -1) {
      return TextRange.from(0, 0);
    } else {
      return TextRange.from(0, p);
    }
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  @NotNull
  protected TextRange getTextRange() {
    return myRange;
  }

  @Override
  @Nullable
  public PsiElement resolveImpl() {
    return XsltNamespaceContext.resolvePrefix(getCanonicalText(), myAttribute);
  }

  @Override
  @NotNull
  public String getUnresolvedMessagePattern() {
    return "Undeclared namespace prefix ''{0}''";
  }
}