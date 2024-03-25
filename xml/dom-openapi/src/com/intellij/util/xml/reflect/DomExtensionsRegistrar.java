// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.reflect;

import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

/**
 * Allows runtime extension of DOM Model.
 */
public interface DomExtensionsRegistrar {

  /**
   * Register single occurrence subtag extension.
   */
  @NotNull
  DomExtension registerFixedNumberChildExtension(@NotNull XmlName name, @NotNull Type type);

  /**
   * Register multiple occurrence subtag extension.
   */
  @NotNull
  DomExtension registerCollectionChildrenExtension(@NotNull XmlName name, @NotNull Type type);

  /**
   * Register {@link com.intellij.util.xml.GenericAttributeValue} using given parameterType.
   */
  @NotNull
  DomExtension registerGenericAttributeValueChildExtension(@NotNull XmlName name, final Type parameterType);

  /**
   * @param name attribute qualified name
   * @param type should extend GenericAttributeValue
   * @return dom extension object
   */
  @NotNull
  DomExtension registerAttributeChildExtension(@NotNull XmlName name, final @NotNull Type type);

  /**
   * @see com.intellij.util.xml.CustomChildren
   */
  @NotNull
  DomExtension registerCustomChildrenExtension(final @NotNull Type type);

  @NotNull
  DomExtension registerCustomChildrenExtension(final @NotNull Type type,
                                               @NotNull CustomDomChildrenDescription.TagNameDescriptor descriptor);

  @NotNull
  DomExtension registerCustomChildrenExtension(final @NotNull Type type,
                                               @NotNull CustomDomChildrenDescription.AttributeDescriptor attributeDescriptor);
}
