// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;


import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ImplicitIdRefProvider {
  ExtensionPointName<ImplicitIdRefProvider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.xml.implicitIdRefProvider");

  @Nullable
  XmlAttribute getIdRefAttribute(@NotNull XmlTag tag);
}
