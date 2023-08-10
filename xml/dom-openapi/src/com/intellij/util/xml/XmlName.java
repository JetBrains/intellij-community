// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.openapi.util.Comparing;
import java.util.Objects;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlName implements Comparable<XmlName> {
  private final String myLocalName;
  private final String myNamespaceKey;

  private final int myHashCode;

  public XmlName(@NotNull @NonNls final String localName) {
    this(localName, null);
  }

  public XmlName(@NotNull @NonNls final String localName, @Nullable final String namespaceKey) {
    myLocalName = localName;
    myNamespaceKey = namespaceKey;

    myHashCode = 31 * myLocalName.hashCode() + (myNamespaceKey != null ? myNamespaceKey.hashCode() : 0);
  }

  @NotNull
  public final String getLocalName() {
    return myLocalName;
  }

  @Nullable
  public final String getNamespaceKey() {
    return myNamespaceKey;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final XmlName xmlName = (XmlName)o;

    if (!myLocalName.equals(xmlName.myLocalName)) return false;
    return Objects.equals(myNamespaceKey, xmlName.myNamespaceKey);
  }

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

  public String toString() {
    return myNamespaceKey + " : " + myLocalName;
  }
}
