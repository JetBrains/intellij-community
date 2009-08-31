/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.XmlName;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class ChildrenDescriptionsHolder<T extends DomChildDescriptionImpl> {
  private final Map<XmlName, T> myMap = new THashMap<XmlName, T>();
  private final ChildrenDescriptionsHolder<T> myDelegate;
  private volatile List<T> myCached = null;

  public ChildrenDescriptionsHolder(@Nullable final ChildrenDescriptionsHolder<T> delegate) {
    myDelegate = delegate;
  }

  public ChildrenDescriptionsHolder() {
    this(null);
  }

  final T addDescription(@NotNull T t) {
    myMap.put(t.getXmlName(), t);
    myCached = null;
    return t;
  }

  final void addDescriptions(@NotNull Collection<T> collection) {
    for (final T t : collection) {
      addDescription(t);
    }
  }

  @Nullable
  final T getDescription(final XmlName name) {
    final T t = myMap.get(name);
    if (t != null) return t;
    return myDelegate != null ? myDelegate.getDescription(name) : null;
  }

  @Nullable
  final T getDescription(@NotNull final String localName, String namespaceKey) {
    return getDescription(new XmlName(localName, namespaceKey));
  }

  @Nullable
  final T findDescription(@NotNull final String localName) {
    for (final XmlName xmlName : myMap.keySet()) {
      if (xmlName.getLocalName().equals(localName)) return myMap.get(xmlName);
    }
    return myDelegate != null ? myDelegate.findDescription(localName) : null;
  }

  @NotNull
  final List<T> getDescriptions() {
    List<T> cached = myCached;
    if (cached != null) {
      return cached;
    }

    cached = new ArrayList<T>(myMap.values());
    if (myDelegate != null) {
      cached.addAll(myDelegate.myMap.values());
    }
    Collections.sort(cached);
    myCached = cached;
    return cached;
  }

}
