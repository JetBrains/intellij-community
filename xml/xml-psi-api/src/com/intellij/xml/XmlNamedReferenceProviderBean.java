// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml;

import com.intellij.diagnostic.PluginException;
import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReferenceProvider;
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.psi.xml.XmlNamedReferenceHost;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Experimental
public class XmlNamedReferenceProviderBean extends CustomLoadingExtensionPointBean<PsiSymbolReferenceProvider> {

  /**
   * One or more strings to match against {@linkplain XmlNamedReferenceHost#getHostName host name}.
   * <p/>
   * Example usage:
   * <pre>
   * &#x3C;namedReferenceProvider <i>required attributes</i>&#x3E;
   *   &#x3C;hostName&#x3E;name1&#x3C;/hostName&#x3E;
   *   ...
   *   &#x3C;hostName&#x3E;nameN&#x3C;/hostName&#x3E;
   *   &#x3C;caseSensitive&#x3E;false&#x3C;/caseSensitive&#x3E;
   * &#x3C;/namedReferenceProvider&#x3E;
   * </pre>
   */
  @Property(surroundWithTag = false)
  @XCollection(elementName = "hostName", valueAttributeName = "")
  public String[] hostNames;

  public String @NotNull [] getHostNames() {
    String[] names = hostNames;
    if (names == null || names.length == 0) {
      throw new PluginException("At least one host name must be specified", getPluginDescriptor().getPluginId());
    }
    return names;
  }

  /**
   * Whether {@link #hostNames} should be matched case-sensitively.
   */
  @Tag
  public boolean caseSensitive = true;

  /**
   * @see com.intellij.model.psi.PsiSymbolReferenceProviderBean#hostElementClass
   */
  @Attribute
  @RequiredElement
  public String hostElementClass;

  /**
   * @see com.intellij.model.psi.PsiSymbolReferenceProviderBean#targetClass
   */
  @Attribute
  @RequiredElement
  public String targetClass;

  @Attribute
  @RequiredElement
  public String implementationClass;

  @Override
  protected @Nullable String getImplementationClassName() {
    return implementationClass;
  }

  public @NotNull Class<? extends XmlNamedReferenceHost> getHostElementClass() {
    return loadClass(hostElementClass);
  }

  public @NotNull Class<? extends Symbol> getResolveTargetClass() {
    return loadClass(targetClass);
  }

  @SuppressWarnings("unchecked")
  private <T> Class<T> loadClass(@NotNull String fqn) {
    PluginDescriptor pluginDescriptor = getPluginDescriptor();
    try {
      return (Class<T>)Class.forName(fqn, true, pluginDescriptor.getPluginClassLoader());
    }
    catch (ClassNotFoundException e) {
      throw new PluginException(e, pluginDescriptor.getPluginId());
    }
  }
}
