// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.reflect;

import com.intellij.util.ParameterizedTypeImpl;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DomExtensionsRegistrarImpl implements DomExtensionsRegistrar {
  private final List<DomExtensionImpl> myAttributes = new SmartList<>();
  private final List<DomExtensionImpl> myFixeds = new SmartList<>();
  private final List<DomExtensionImpl> myCollections = new SmartList<>();
  private final Set<Object> myDependencies = new HashSet<>();
  private final List<DomExtensionImpl> myCustoms = new SmartList<>();

  public List<DomExtensionImpl> getAttributes() {
    return myAttributes;
  }
  public List<DomExtensionImpl> getFixeds() {
    return myFixeds;
  }

  public List<DomExtensionImpl> getCollections() {
    return myCollections;
  }

  public List<DomExtensionImpl> getCustoms() {
    return myCustoms;
  }

  @NotNull
  public DomExtension registerFixedNumberChildrenExtension(@NotNull final XmlName name, @NotNull final Type type, final int count) {
    assert count > 0;
    return addExtension(myFixeds, name, type).setCount(count);
  }

  @Override
  @NotNull
  public DomExtension registerFixedNumberChildExtension(@NotNull final XmlName name, @NotNull final Type type) {
    return registerFixedNumberChildrenExtension(name, type, 1);
  }

  @Override
  @NotNull
  public DomExtension registerCollectionChildrenExtension(@NotNull final XmlName name, @NotNull final Type type) {
    return addExtension(myCollections, name, type);
  }

  @Override
  @NotNull
  public DomExtension registerGenericAttributeValueChildExtension(@NotNull final XmlName name, final Type parameterType) {
    return addExtension(myAttributes, name, new ParameterizedTypeImpl(GenericAttributeValue.class, parameterType));
  }

  @Override
  @NotNull
  public DomExtension registerAttributeChildExtension(@NotNull final XmlName name, @NotNull final Type type) {
    assert GenericAttributeValue.class.isAssignableFrom(ReflectionUtil.getRawType(type));
    return addExtension(myAttributes, name, type);
  }

  @Override
  @NotNull
  public DomExtension registerCustomChildrenExtension(@NotNull final Type type) {
    return registerCustomChildrenExtension(type, CustomDomChildrenDescription.AttributeDescriptor.EMPTY);
  }

  @NotNull
  @Override
  public DomExtension registerCustomChildrenExtension(@NotNull Type type,
                                                      @NotNull CustomDomChildrenDescription.TagNameDescriptor descriptor) {
    DomExtensionImpl extension = addExtension(myCustoms, null, type);
    extension.setTagNameDescriptor(descriptor);
    return extension;
  }

  @NotNull
  @Override
  public DomExtension registerCustomChildrenExtension(@NotNull Type type,
                                                      @NotNull CustomDomChildrenDescription.AttributeDescriptor attributeDescriptor) {

    DomExtensionImpl extension = addExtension(myCustoms, null, type);
    extension.setAttributesDescriptor(attributeDescriptor);
    return extension;
  }

  private static DomExtensionImpl addExtension(final List<? super DomExtensionImpl> list, @Nullable final XmlName name, final Type type) {
    final DomExtensionImpl extension = new DomExtensionImpl(type, name);
    list.add(extension);
    return extension;
  }

  public void addDependencies(Object[] deps) {
    ContainerUtil.addAll(myDependencies, deps);
  }

  public Object[] getDependencies() {
    return myDependencies.toArray();
  }
}
