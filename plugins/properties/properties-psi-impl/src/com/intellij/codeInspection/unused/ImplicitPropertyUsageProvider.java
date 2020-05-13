/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
