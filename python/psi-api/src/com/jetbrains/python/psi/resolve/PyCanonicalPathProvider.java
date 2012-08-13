package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyCanonicalPathProvider {
  ExtensionPointName<PyCanonicalPathProvider> EP_NAME = ExtensionPointName.create("Pythonid.canonicalPathProvider");

  @Nullable
  PyQualifiedName getCanonicalPath(PyQualifiedName qName);
}
