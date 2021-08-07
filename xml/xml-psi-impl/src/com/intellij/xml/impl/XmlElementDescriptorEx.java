// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.impl;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;

public interface XmlElementDescriptorEx extends XmlElementDescriptor {

  default void validateTagName(@NotNull final XmlTag tag, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
  }

}
