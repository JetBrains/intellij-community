// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;


public interface PyKnownDecoratorProvider {
  ExtensionPointName<PyKnownDecoratorProvider> EP_NAME = ExtensionPointName.create("Pythonid.knownDecoratorProvider");

  @Nullable
  String toKnownDecorator(String decoratorName);
}
