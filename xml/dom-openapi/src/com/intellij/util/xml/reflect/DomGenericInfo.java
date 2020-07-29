// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.reflect;

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
  List<? extends DomAttributeChildDescription<?>> getAttributeChildrenDescriptions();

  @Nullable DomFixedChildDescription getFixedChildDescription(@NonNls String tagName);

  @Nullable DomFixedChildDescription getFixedChildDescription(@NonNls String tagName, @NonNls String namespaceKey);

  @Nullable DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName);

  @Nullable DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName, @NonNls String namespaceKey);

  @Nullable
  DomAttributeChildDescription<?> getAttributeChildDescription(@NonNls String attributeName);

  @Nullable
  DomAttributeChildDescription<?> getAttributeChildDescription(@NonNls String attributeName, @NonNls String namespaceKey);

  /**
   * @return true, if there's no children in the element, only tag value accessors
   */
  boolean isTagValueElement();

  @Nullable
  GenericDomValue<?> getNameDomElement(DomElement element);

  @NotNull
  List<? extends CustomDomChildrenDescription> getCustomNameChildrenDescription();
}
