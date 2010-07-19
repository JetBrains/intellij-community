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

package com.intellij.util.xml.reflect;

import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public interface DomExtensionsRegistrar {

  @NotNull DomExtension registerFixedNumberChildExtension(@NotNull XmlName name, @NotNull Type type);

  @NotNull DomExtension registerCollectionChildrenExtension(@NotNull XmlName name, @NotNull Type type);

  @NotNull DomExtension registerGenericAttributeValueChildExtension(@NotNull XmlName name, final Type parameterType);

  /**
   * @param name attribute qualified name
   * @param type should extend GenericAttributeValue
   * @return dom extension object
   */
  @NotNull DomExtension registerAttributeChildExtension(@NotNull XmlName name, @NotNull final Type type);

  /**
   * @param type
   * @return
   * @see com.intellij.util.xml.CustomChildren
   */
  @NotNull DomExtension registerCustomChildrenExtension(@NotNull final Type type);

  @NotNull DomExtension registerCustomChildrenExtension(@NotNull final Type type, @NotNull CustomDomChildrenDescription.TagNameDescriptor descriptor);

}
