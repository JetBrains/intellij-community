// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

public interface PythonFlavorProvider {
  ExtensionPointName<PythonFlavorProvider> EP_NAME = ExtensionPointName.create("Pythonid.pythonFlavorProvider");

  @NotNull
  PythonSdkFlavor<?> getFlavor();
}
