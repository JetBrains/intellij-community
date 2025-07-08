// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.impl;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReferenceProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.xml.XmlNamedReferenceHost;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlNamedReferenceProviderBean;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Internal
@Service
final class NamedReferenceProviders {
  private static final ExtensionPointName<XmlNamedReferenceProviderBean> EP_NAME = new ExtensionPointName<>("com.intellij.xml.namedReferenceProvider");

  static @NotNull NamedReferenceProviders getInstance() {
    return ApplicationManager.getApplication().getService(NamedReferenceProviders.class);
  }


  // There are 2 XmlNamedReferenceHost inheritors currently.
  private final Map<Class<?>, ByHostClass> myByHostClass = new ConcurrentHashMap<>(2);
  private final Map<Class<?>, Collection<PsiSymbolReferenceProvider>> myByTargetClass = new ConcurrentHashMap<>();

  public NamedReferenceProviders() {
    EP_NAME.addChangeListener(() -> myByHostClass.clear(), null);
    EP_NAME.addChangeListener(() -> myByTargetClass.clear(), null);
  }

  @NotNull
  @Unmodifiable
  Collection<XmlNamedReferenceProviderBean> getNamedReferenceProviderBeans(@NotNull XmlNamedReferenceHost element) {
    final String hostName = element.getHostName();
    if (hostName == null) {
      return Collections.emptyList();
    }
    return byHostClass(element).byHostName(hostName);
  }

  private @NotNull ByHostClass byHostClass(@NotNull XmlNamedReferenceHost element) {
    return myByHostClass.computeIfAbsent(element.getClass(), NamedReferenceProviders::byHostClassInner);
  }

  private static @NotNull ByHostClass byHostClassInner(@NotNull Class<?> hostClass) {
    List<XmlNamedReferenceProviderBean> result = new SmartList<>();
    for (XmlNamedReferenceProviderBean bean : EP_NAME.getExtensionList()) {
      if (bean.getHostElementClass().isAssignableFrom(hostClass)) {
        result.add(bean);
      }
    }
    return new ByHostClass(result);
  }

  private static final class ByHostClass {
    private final Map<String, List<XmlNamedReferenceProviderBean>> myCaseSensitiveMap;
    private final Map<String, List<XmlNamedReferenceProviderBean>> myCaseInsensitiveMap;

    ByHostClass(@NotNull List<XmlNamedReferenceProviderBean> beans) {
      Map<String, List<XmlNamedReferenceProviderBean>> caseSensitiveMap = new HashMap<>();
      Map<String, List<XmlNamedReferenceProviderBean>> caseInsensitiveMap = CollectionFactory.createCaseInsensitiveStringMap();

      for (XmlNamedReferenceProviderBean bean : beans) {
        final Map<String, List<XmlNamedReferenceProviderBean>> map = bean.caseSensitive ? caseSensitiveMap
                                                                                        : caseInsensitiveMap;
        for (String hostName : bean.getHostNames()) {
          map.computeIfAbsent(hostName, __ -> new SmartList<>()).add(bean);
        }
      }

      CollectionFactory.trimMap(caseInsensitiveMap);

      myCaseSensitiveMap = caseSensitiveMap;
      myCaseInsensitiveMap = caseInsensitiveMap;
    }

    @NotNull
    @Unmodifiable
    Collection<XmlNamedReferenceProviderBean> byHostName(@NotNull String hostName) {
      return ContainerUtil.concat(
        ObjectUtils.notNull(myCaseSensitiveMap.get(hostName), Collections.emptyList()),
        ObjectUtils.notNull(myCaseInsensitiveMap.get(hostName), Collections.emptyList())
      );
    }
  }

  @NotNull Collection<@NotNull PsiSymbolReferenceProvider> getNamedReferenceProviders(@NotNull Symbol target) {
    return myByTargetClass.computeIfAbsent(target.getClass(), NamedReferenceProviders::byTargetClassInner);
  }

  private static @NotNull Collection<PsiSymbolReferenceProvider> byTargetClassInner(@NotNull Class<?> targetClass) {
    List<PsiSymbolReferenceProvider> result = new SmartList<>();
    for (XmlNamedReferenceProviderBean bean : EP_NAME.getExtensionList()) {
      if (targetClass.isAssignableFrom(bean.getResolveTargetClass())) {
        result.add(bean.getInstance());
      }
    }
    return result;
  }
}
