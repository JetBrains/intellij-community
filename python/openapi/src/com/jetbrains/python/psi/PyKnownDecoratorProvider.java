package com.jetbrains.python.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyKnownDecoratorProvider {
  ExtensionPointName<PyKnownDecoratorProvider> EP_NAME = ExtensionPointName.create("Pythonid.knownDecoratorProvider");

  @Nullable
  String toKnownDecorator(String decoratorName);
}
