// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.util.SmartList;
import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ChildrenDescriptionsHolder<T extends DomChildDescriptionImpl> {
  private final Map<XmlName, T> myMap = new HashMap<>();
  private final ChildrenDescriptionsHolder<? extends T> myDelegate;
  private volatile List<T> myCached = null;

  public ChildrenDescriptionsHolder(@Nullable final ChildrenDescriptionsHolder<? extends T> delegate) {
    myDelegate = delegate;
  }

  public ChildrenDescriptionsHolder() {
    this(null);
  }

  T addDescription(@NotNull T t) {
    myMap.put(t.getXmlName(), t);
    myCached = null;
    return t;
  }

  void addDescriptions(@NotNull Collection<? extends T> collection) {
    for (final T t : collection) {
      addDescription(t);
    }
  }

  @Nullable T getDescription(final XmlName name) {
    final T t = myMap.get(name);
    if (t != null) return t;
    return myDelegate != null ? myDelegate.getDescription(name) : null;
  }

  @Nullable T getDescription(@NotNull final String localName, String namespaceKey) {
    return getDescription(new XmlName(localName, namespaceKey));
  }

  @Nullable T findDescription(@NotNull final String localName) {
    for (final XmlName xmlName : myMap.keySet()) {
      if (xmlName.getLocalName().equals(localName)) return myMap.get(xmlName);
    }
    return myDelegate != null ? myDelegate.findDescription(localName) : null;
  }

  @NotNull List<T> getDescriptions() {
    final List<T> result = new ArrayList<>();
    dumpDescriptions(result);
    return result;
  }

  private List<T> getSortedDescriptions() {
    List<T> cached = myCached;
    if (cached != null) {
      return cached;
    }

    if (!myMap.isEmpty()) {
      cached = new SmartList<>(myMap.values());
      Collections.sort(cached);
    } else {
      cached = Collections.emptyList();
    }
    myCached = cached;
    return cached;
  }


  void dumpDescriptions(Collection<? super T> to) {
    to.addAll(getSortedDescriptions());
    if (myDelegate != null) {
      myDelegate.dumpDescriptions(to);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ChildrenDescriptionsHolder)) return false;

    ChildrenDescriptionsHolder holder = (ChildrenDescriptionsHolder)o;

    if (myDelegate != null ? !myDelegate.equals(holder.myDelegate) : holder.myDelegate != null) return false;
    if (!myMap.equals(holder.myMap)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMap.hashCode();
    result = 31 * result + (myDelegate != null ? myDelegate.hashCode() : 0);
    return result;
  }
}
