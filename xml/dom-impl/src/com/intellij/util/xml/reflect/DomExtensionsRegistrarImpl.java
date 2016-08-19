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
package com.intellij.util.xml.reflect;

import com.intellij.util.ParameterizedTypeImpl;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.XmlName;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class DomExtensionsRegistrarImpl implements DomExtensionsRegistrar {
  private final List<DomExtensionImpl> myAttributes = new SmartList<>();
  private final List<DomExtensionImpl> myFixeds = new SmartList<>();
  private final List<DomExtensionImpl> myCollections = new SmartList<>();
  private final Set<Object> myDependencies = new THashSet<>();
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
  public final DomExtension registerFixedNumberChildrenExtension(@NotNull final XmlName name, @NotNull final Type type, final int count) {
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

  private static DomExtensionImpl addExtension(final List<DomExtensionImpl> list, @Nullable final XmlName name, final Type type) {
    final DomExtensionImpl extension = new DomExtensionImpl(type, name);
    list.add(extension);
    return extension;
  }

  public final void addDependencies(Object[] deps) {
    ContainerUtil.addAll(myDependencies, deps);
  }

  public Object[] getDependencies() {
    return myDependencies.toArray();
  }
}
