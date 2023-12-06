// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFile;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ImplicitNamespaceDescriptorProvider {
  @NonNls ExtensionPointName<ImplicitNamespaceDescriptorProvider> EP_NAME = ExtensionPointName.create("com.intellij.xml.implicitNamespaceDescriptorProvider");

  @Nullable
  XmlNSDescriptor getNamespaceDescriptor(@Nullable Module module, final @NotNull String ns, @Nullable PsiFile file);
}
