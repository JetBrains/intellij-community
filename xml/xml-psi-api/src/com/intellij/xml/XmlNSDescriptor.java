// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml;

import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface XmlNSDescriptor extends PsiMetaData {
  @Nullable
  XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag);

  XmlElementDescriptor @NotNull [] getRootElementsDescriptors(@Nullable final XmlDocument document);

  default XmlElementDescriptor @NotNull [] getAllElementsDescriptors(@Nullable final XmlDocument document) {
    return getRootElementsDescriptors(document);
  }

  @Nullable
  XmlFile getDescriptorFile();
}
