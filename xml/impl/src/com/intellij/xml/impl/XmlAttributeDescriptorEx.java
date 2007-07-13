/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.xml.impl;

import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface XmlAttributeDescriptorEx extends XmlAttributeDescriptor {

  /**
   * @param newTargetName
   * @return new attribute local name
   */
  @Nullable
  @NonNls
  String handleTargetRename(@NotNull @NonNls final String newTargetName);
}
