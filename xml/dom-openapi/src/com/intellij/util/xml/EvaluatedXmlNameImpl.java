// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author peter
 */
public final class EvaluatedXmlNameImpl implements EvaluatedXmlName {
  private static final Key<CachedValue<Map<String,List<String>>>> NAMESPACE_PROVIDER_KEY = Key.create("NamespaceProvider");
  private static final Map<EvaluatedXmlNameImpl, EvaluatedXmlNameImpl> ourInterned =
    new ConcurrentHashMap<>();

  private final XmlName myXmlName;
  private final String myNamespaceKey;
  private final boolean myEqualToParent;

  private EvaluatedXmlNameImpl(final @NotNull XmlName xmlName, final @Nullable String namespaceKey, final boolean equalToParent) {
    myXmlName = xmlName;
    myNamespaceKey = namespaceKey;
    myEqualToParent = equalToParent;
  }

  public final @NotNull String getLocalName() {
    return myXmlName.getLocalName();
  }

  @Override
  public final XmlName getXmlName() {
    return myXmlName;
  }

  @Override
  public final EvaluatedXmlName evaluateChildName(final @NotNull XmlName name) {
    String namespaceKey = name.getNamespaceKey();
    final boolean equalToParent = Objects.equals(namespaceKey, myNamespaceKey);
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

  private @NotNull List<String> getAllowedNamespaces(final XmlFile file) {
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
  @NonNls
  public final @NotNull String getNamespace(@NotNull XmlElement parentElement, final XmlFile file) {
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

  public static EvaluatedXmlNameImpl createEvaluatedXmlName(final @NotNull XmlName xmlName, final @Nullable String namespaceKey, boolean equalToParent) {
    final EvaluatedXmlNameImpl name = new EvaluatedXmlNameImpl(xmlName, namespaceKey, equalToParent);
    final EvaluatedXmlNameImpl interned = ourInterned.get(name);
    if (interned != null) {
      return interned;
    }
    ourInterned.put(name, name);
    return name;
  }
}
