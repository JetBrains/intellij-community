// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath.xslt.impl.references;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import org.intellij.lang.xpath.xslt.context.XsltNamespaceContext;
import org.intellij.plugins.xpathView.XPathBundle;
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
      for (int i = p - 1; i >= 0 ; i--) {
        if (!Character.isJavaIdentifierPart(value.charAt(i))) {
          return TextRange.create(i + 1, p);
        }
      }
      return TextRange.from(0, p);
    }
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  protected @NotNull TextRange getTextRange() {
    return myRange;
  }

  @Override
  public @Nullable PsiElement resolveImpl() {
    return XsltNamespaceContext.resolvePrefix(getCanonicalText(), myAttribute);
  }

  @Override
  public @NotNull String getUnresolvedMessagePattern() {
    return XPathBundle.partialMessage("inspection.message.undeclared.namespace.prefix", 1);
  }
}