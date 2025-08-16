// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject.config;

import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.XmlTokenImpl;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTokenType;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.ContextType;
import org.intellij.lang.xpath.context.NamespaceContext;
import org.intellij.lang.xpath.context.VariableContext;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathType;
import org.intellij.plugins.xpathView.support.XPathSupport;
import org.jaxen.JaxenException;
import org.jaxen.XPath;
import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;
import java.util.Collections;
import java.util.Set;

final class XPathSupportProxyImpl extends XPathSupportProxy {
  private static class Provider extends ContextProvider {
    private final XmlTokenImpl myDummyContext = new XmlTokenImpl(XmlTokenType.XML_CONTENT_EMPTY, "") {
      @Override
      public boolean isValid() {
        return true;
      }
    };

    @Override
    public @NotNull ContextType getContextType() {
      return XPathSupport.TYPE;
    }

    @Override
    public @NotNull XPathType getExpectedType(XPathExpression expr) {
      return XPathType.BOOLEAN;
    }

    @Override
    public XmlElement getContextElement() {
      // needed because the static method ContextProvider.isValid() checks this to determine if the provider
      // is still valid - refactor this into an instance method ContextProvider.isValid()?
      return myDummyContext;
    }

    @Override
    public NamespaceContext getNamespaceContext() {
      return null;
    }

    @Override
    public VariableContext getVariableContext() {
      return null;
    }

    @Override
    public Set<QName> getAttributes(boolean forValidation) {
      return null;
    }

    @Override
    public Set<QName> getElements(boolean forValidation) {
      return null;
    }
  }

  private final ContextProvider myProvider = new Provider();
  private final XPathSupport mySupport = XPathSupport.getInstance();

  @Override
  public @NotNull XPath createXPath(String expression) throws JaxenException {
    return mySupport.createXPath(null, expression, Collections.emptyList());
  }

  @Override
  public void attachContext(@NotNull PsiFile file) {
    myProvider.attachTo(file);
  }
}
