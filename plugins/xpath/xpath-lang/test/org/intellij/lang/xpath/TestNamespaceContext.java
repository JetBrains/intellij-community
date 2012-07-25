/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  public IntentionAction[] getUnresolvedNamespaceFixes(PsiReference reference, String localName) {
    return IntentionAction.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public String getDefaultNamespace(XmlElement context) {
    return null;
  }
}