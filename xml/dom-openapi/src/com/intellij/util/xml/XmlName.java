// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class XmlName implements Comparable<XmlName> {
  private final String myLocalName;
  private final String myNamespaceKey;

  private final int myHashCode;

  public XmlName(final @NotNull @NonNls String localName) {
    this(localName, null);
  }

  public XmlName(final @NotNull @NonNls String localName, final @Nullable String namespaceKey) {
    myLocalName = localName;
    myNamespaceKey = namespaceKey;

    myHashCode = 31 * myLocalName.hashCode() + (myNamespaceKey != null ? myNamespaceKey.hashCode() : 0);
  }

  public final @NotNull String getLocalName() {
    return myLocalName;
  }

  public final @Nullable String getNamespaceKey() {
    return myNamespaceKey;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final XmlName xmlName = (XmlName)o;

    if (!myLocalName.equals(xmlName.myLocalName)) return false;
    return Objects.equals(myNamespaceKey, xmlName.myNamespaceKey);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }


  @Override
  public int compareTo(XmlName o) {
    final int i = myLocalName.compareTo(o.myLocalName);
    if (i != 0) {
      return i;
    }
    return Comparing.compare(myNamespaceKey, o.myNamespaceKey);
  }

  @Override
  public String toString() {
    return myNamespaceKey + " : " + myLocalName;
  }
}
