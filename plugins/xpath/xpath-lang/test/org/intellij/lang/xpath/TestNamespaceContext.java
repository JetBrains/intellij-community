// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.NamespaceContext;
import org.intellij.lang.xpath.psi.XPath2Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.Collections;

class TestNamespaceContext implements NamespaceContext {
  private static final TestNamespaceContext INSTANCE = new TestNamespaceContext();

  @TestOnly
  public static void install(Disposable parent) {
    final NamespaceContext old = ContextProvider.DefaultProvider.NULL_NAMESPACE_CONTEXT;
    ContextProvider.DefaultProvider.NULL_NAMESPACE_CONTEXT = TestNamespaceContext.INSTANCE;
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        ContextProvider.DefaultProvider.NULL_NAMESPACE_CONTEXT = old;
      }
    });
  }

  @Override
  public String getNamespaceURI(String prefix, XmlElement context) {
    return "xs".equals(prefix) ? XPath2Type.XMLSCHEMA_NS : null;
  }

  @Override
  public String getPrefixForURI(String uri, XmlElement context) {
    return XPath2Type.XMLSCHEMA_NS.equals(uri) ? "xs" : null;
  }

  @NotNull
  @Override
  public Collection<String> getKnownPrefixes(XmlElement context) {
    return Collections.singleton("xs");
  }

  @Override
  public PsiElement resolve(String prefix, XmlElement context) {
    return null;
  }

  @Override
  public IntentionAction[] getUnresolvedNamespaceFixes(@NotNull PsiReference reference, String localName) {
    return IntentionAction.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public String getDefaultNamespace(XmlElement context) {
    return null;
  }
}