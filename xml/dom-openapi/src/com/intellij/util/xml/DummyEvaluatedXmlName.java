// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class DummyEvaluatedXmlName implements EvaluatedXmlName {
  private final XmlName myXmlName;
  private final String myNamespace;

  public DummyEvaluatedXmlName(final String localName, final @NotNull String namespace) {
    this(new XmlName(localName), namespace);
  }

  public DummyEvaluatedXmlName(final XmlName xmlName, final @NotNull String namespace) {
    myXmlName = xmlName;
    myNamespace = namespace;
  }

  @Override
  public XmlName getXmlName() {
    return myXmlName;
  }

  @Override
  public EvaluatedXmlName evaluateChildName(final @NotNull XmlName name) {
    String namespaceKey = name.getNamespaceKey();
    if (namespaceKey == null) {
      return new DummyEvaluatedXmlName(name.getLocalName(), myNamespace);
    }
    return EvaluatedXmlNameImpl.createEvaluatedXmlName(name, namespaceKey, false);
  }

  @Override
  public boolean isNamespaceAllowed(final String namespace, final XmlFile file, boolean qualified) {
    return namespace.equals(myNamespace);
  }

  @Override
  public @NotNull @NonNls String getNamespace(final @NotNull XmlElement parentElement, final XmlFile file) {
    return myNamespace;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final DummyEvaluatedXmlName that = (DummyEvaluatedXmlName)o;

    if (myNamespace != null ? !myNamespace.equals(that.myNamespace) : that.myNamespace != null) return false;
    if (myXmlName != null ? !myXmlName.equals(that.myXmlName) : that.myXmlName != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = (myXmlName != null ? myXmlName.hashCode() : 0);
    result = 31 * result + (myNamespace != null ? myNamespace.hashCode() : 0);
    return result;
  }
}
