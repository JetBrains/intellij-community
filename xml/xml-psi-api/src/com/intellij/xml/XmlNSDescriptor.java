// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml;

import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface XmlNSDescriptor extends PsiMetaData, PossiblyDumbAware {
  @Nullable
  XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag);

  XmlElementDescriptor @NotNull [] getRootElementsDescriptors(final @Nullable XmlDocument document);

  default XmlElementDescriptor @NotNull [] getAllElementsDescriptors(final @Nullable XmlDocument document) {
    return getRootElementsDescriptors(document);
  }

  @Nullable
  XmlFile getDescriptorFile();
}
