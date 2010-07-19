/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DummyEvaluatedXmlName implements EvaluatedXmlName {
  private final XmlName myXmlName;
  private final String myNamespace;

  public DummyEvaluatedXmlName(final String localName, final String namespace) {
    this(new XmlName(localName), namespace);
  }

  public DummyEvaluatedXmlName(final XmlName xmlName, final String namespace) {
    myXmlName = xmlName;
    myNamespace = namespace;
  }

  public XmlName getXmlName() {
    return myXmlName;
  }

  public EvaluatedXmlName evaluateChildName(@NotNull final XmlName name) {
    String namespaceKey = name.getNamespaceKey();
    if (namespaceKey == null) {
      return new DummyEvaluatedXmlName(name.getLocalName(), myNamespace);
    }
    return EvaluatedXmlNameImpl.createEvaluatedXmlName(name, namespaceKey, false);
  }

  public boolean isNamespaceAllowed(final String namespace, final XmlFile file) {
    return namespace.equals(myNamespace);
  }

  @NotNull
  @NonNls
  public String getNamespace(@NotNull final XmlElement parentElement, final XmlFile file) {
    return myNamespace;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final DummyEvaluatedXmlName that = (DummyEvaluatedXmlName)o;

    if (myNamespace != null ? !myNamespace.equals(that.myNamespace) : that.myNamespace != null) return false;
    if (myXmlName != null ? !myXmlName.equals(that.myXmlName) : that.myXmlName != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myXmlName != null ? myXmlName.hashCode() : 0);
    result = 31 * result + (myNamespace != null ? myNamespace.hashCode() : 0);
    return result;
  }
}
