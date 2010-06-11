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

import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public interface DomGenericInfo {

  @Nullable
  String getElementName(DomElement element);

  @NotNull
  List<? extends AbstractDomChildrenDescription> getChildrenDescriptions();

  @NotNull
  List<? extends DomFixedChildDescription> getFixedChildrenDescriptions();

  @NotNull
  List<? extends DomCollectionChildDescription> getCollectionChildrenDescriptions();

  @NotNull
  List<? extends DomAttributeChildDescription> getAttributeChildrenDescriptions();

  @Nullable DomFixedChildDescription getFixedChildDescription(@NonNls String tagName);

  @Nullable DomFixedChildDescription getFixedChildDescription(@NonNls String tagName, @NonNls String namespaceKey);

  @Nullable DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName);

  @Nullable DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName, @NonNls String namespaceKey);

  @Nullable
  DomAttributeChildDescription getAttributeChildDescription(@NonNls String attributeName);

  @Nullable
  DomAttributeChildDescription getAttributeChildDescription(@NonNls String attributeName, @NonNls String namespaceKey);

  /**
   * @return true, if there's no children in the element, only tag value accessors
   */
  boolean isTagValueElement();

  /**
   *
   * @param element
   * @return {@link com.intellij.psi.xml.XmlAttributeValue} or {@link com.intellij.psi.xml.XmlTag}
   */
  @Deprecated
  @Nullable
  XmlElement getNameElement(DomElement element);

  @Nullable
  GenericDomValue getNameDomElement(DomElement element);

  @Nullable
  CustomDomChildrenDescription getCustomNameChildrenDescription();
}
