// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.impl;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface XmlAttributeDescriptorEx extends XmlAttributeDescriptor {

  /**
   * @return new attribute local name
   */
  default @Nullable @NonNls String handleTargetRename(final @NotNull @NonNls String newTargetName) {
    return null;
  }

  default void validateAttributeName(final @NotNull XmlAttribute attribute, final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
  }
}
