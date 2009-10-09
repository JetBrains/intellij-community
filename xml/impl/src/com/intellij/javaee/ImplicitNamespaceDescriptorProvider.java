package com.intellij.javaee;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ImplicitNamespaceDescriptorProvider {
  @NonNls ExtensionPointName<ImplicitNamespaceDescriptorProvider> EP_NAME = ExtensionPointName.create("com.intellij.javaee.implicitNamespaceDescriptorProvider");

  @Nullable
  XmlNSDescriptor getNamespaceDescriptor(@Nullable Module module, @NotNull final String ns);
}
