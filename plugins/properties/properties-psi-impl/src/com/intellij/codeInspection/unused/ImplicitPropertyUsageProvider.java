// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.unused;

import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * @author Max Medvedev
 */
public abstract class ImplicitPropertyUsageProvider {
  public static final ExtensionPointName<ImplicitPropertyUsageProvider> EP_NAME = ExtensionPointName.create("com.intellij.properties.implicitPropertyUsageProvider");

  public static boolean isImplicitlyUsed(@NotNull Property property) {
    for (ImplicitPropertyUsageProvider provider : EP_NAME.getExtensions()) {
      if (provider.isUsed(property)) return true;
    }
    return false;
  }

  protected abstract boolean isUsed(@NotNull Property property);
}
