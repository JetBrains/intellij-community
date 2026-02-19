// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.xml;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface XmlStructureViewBuilderProvider {
  @NonNls String EXTENSION_POINT_NAME = "com.intellij.xmlStructureViewBuilderProvider";

  ExtensionPointName<KeyedLazyInstance<XmlStructureViewBuilderProvider>>
    EP_NAME = new ExtensionPointName<>(EXTENSION_POINT_NAME);

  @Nullable
  StructureViewBuilder createStructureViewBuilder(@NotNull XmlFile file);
}
