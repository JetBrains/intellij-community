/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public interface DomGenericInfo {

  @NotNull
  List<DomChildrenDescription> getChildrenDescriptions();

  @NotNull
  List<DomFixedChildDescription> getFixedChildrenDescriptions();

  @NotNull
  List<DomCollectionChildDescription> getCollectionChildrenDescriptions();

  @Nullable
  DomFixedChildDescription getFixedChildDescription(String tagName);

  @Nullable
  DomCollectionChildDescription getCollectionChildDescription(String tagName);
}
