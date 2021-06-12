// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.Nullable;


public interface XmlElementDescriptorProvider {
  ExtensionPointName<XmlElementDescriptorProvider> EP_NAME = ExtensionPointName.create("com.intellij.xml.elementDescriptorProvider");

  @Nullable
  XmlElementDescriptor getDescriptor(XmlTag tag);
}
