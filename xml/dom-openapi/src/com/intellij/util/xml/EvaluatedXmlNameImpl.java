/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class EvaluatedXmlNameImpl implements EvaluatedXmlName {
  private static final Key<CachedValue<Map<String,List<String>>>> NAMESPACE_PROVIDER_KEY = Key.create("NamespaceProvider");
  private static final Map<EvaluatedXmlNameImpl, EvaluatedXmlNameImpl> ourInterned =
    ContainerUtil.newConcurrentMap();

  private final XmlName myXmlName;
  private final String myNamespaceKey;
  private final boolean myEqualToParent;

  private EvaluatedXmlNameImpl(@NotNull final XmlName xmlName, @Nullable final String namespaceKey, final boolean equalToParent) {
    myXmlName = xmlName;
    myNamespaceKey = namespaceKey;
    myEqualToParent = equalToParent;
  }

  @NotNull
  public final String getLocalName() {
    return myXmlName.getLocalName();
  }

  @Override
  public final XmlName getXmlName() {
    return myXmlName;
  }

  @Override
  public final EvaluatedXmlName evaluateChildName(@NotNull final XmlName name) {
    String namespaceKey = name.getNamespaceKey();
    final boolean equalToParent = Comparing.equal(namespaceKey, myNamespaceKey);
    if (namespaceKey == null) {
      namespaceKey = myNamespaceKey;
    }
    return createEvaluatedXmlName(name, namespaceKey, equalToParent);
  }

  public String toString() {
    return (myNamespaceKey == null ? "" : myNamespaceKey + " : ") + myXmlName.getLocalName();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof EvaluatedXmlNameImpl)) return false;

    final EvaluatedXmlNameImpl that = (EvaluatedXmlNameImpl)o;

    if (myEqualToParent != that.myEqualToParent) return false;
    if (myNamespaceKey != null ? !myNamespaceKey.equals(that.myNamespaceKey) : that.myNamespaceKey != null) return false;
    if (!myXmlName.equals(that.myXmlName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myXmlName.hashCode();
    result = 31 * result + (myNamespaceKey != null ? myNamespaceKey.hashCode() : 0);
    result = 31 * result + (myEqualToParent ? 1 : 0);
    return result;
  }

  public final boolean isNamespaceAllowed(DomFileElement element, String namespace) {
    if (myNamespaceKey == null || myEqualToParent) return true;
    final XmlFile file = element.getFile();
    return isNamespaceAllowed(namespace, getAllowedNamespaces(file));
  }

  @NotNull
  private List<String> getAllowedNamespaces(final XmlFile file) {
    CachedValue<Map<String, List<String>>> value = file.getUserData(NAMESPACE_PROVIDER_KEY);
    if (value == null) {
      file.putUserData(NAMESPACE_PROVIDER_KEY, value = CachedValuesManager.getManager(file.getProject()).createCachedValue(() -> {
        Map<String, List<String>> map = ConcurrentFactoryMap.createMap(key-> {
            final DomFileDescription<?> description = DomManager.getDomManager(file.getProject()).getDomFileDescription(file);
            if (description == null) return Collections.emptyList();
            return description.getAllowedNamespaces(key, file);
          }
        );
        return CachedValueProvider.Result.create(map, file);
      }, false));
    }

    final List<String> list = value.getValue().get(myNamespaceKey);
    assert list != null;
    return list;
  }

  private static boolean isNamespaceAllowed(final String namespace, final List<String> list) {
    return list.contains(namespace) || StringUtil.isEmpty(namespace) && list.isEmpty();

  }

  @Override
  public final boolean isNamespaceAllowed(String namespace, final XmlFile file, boolean qualified) {
    return myNamespaceKey == null || myEqualToParent && !qualified || isNamespaceAllowed(namespace, getNamespaceList(file));
  }

  @Override
  @NotNull @NonNls
  public final String getNamespace(@NotNull XmlElement parentElement, final XmlFile file) {
    final String xmlElementNamespace = getXmlElementNamespace(parentElement);
    if (myNamespaceKey != null && !myEqualToParent) {
      final List<String> strings = getAllowedNamespaces(file);
      if (!strings.isEmpty() && !strings.contains(xmlElementNamespace)) {
        return strings.get(0);
      }
    }
    return xmlElementNamespace;
  }

  private static String getXmlElementNamespace(final XmlElement parentElement) {
    if (parentElement instanceof XmlTag) {
      return ((XmlTag)parentElement).getNamespace();
    }
    if (parentElement instanceof XmlAttribute) {
      return ((XmlAttribute)parentElement).getNamespace();
    }
    if (parentElement instanceof XmlFile) {
      final XmlDocument document = ((XmlFile)parentElement).getDocument();
      if (document != null) {
        final XmlTag tag = document.getRootTag();
        if (tag != null) {
          return tag.getNamespace();
        }
      }
      return "";
    }
    throw new AssertionError("Can't get namespace of " + parentElement);
  }

  private List<String> getNamespaceList(final XmlFile file) {
    return getAllowedNamespaces(file);
  }

  public static EvaluatedXmlNameImpl createEvaluatedXmlName(@NotNull final XmlName xmlName, @Nullable final String namespaceKey, boolean equalToParent) {
    final EvaluatedXmlNameImpl name = new EvaluatedXmlNameImpl(xmlName, namespaceKey, equalToParent);
    final EvaluatedXmlNameImpl interned = ourInterned.get(name);
    if (interned != null) {
      return interned;
    }
    ourInterned.put(name, name);
    return name;
  }
}
                