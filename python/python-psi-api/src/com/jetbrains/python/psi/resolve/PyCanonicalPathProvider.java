// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to provide a custom qualified name when a specific symbol is going to be imported into a specific file.
 */
public interface PyCanonicalPathProvider {
  ExtensionPointName<PyCanonicalPathProvider> EP_NAME = ExtensionPointName.create("Pythonid.canonicalPathProvider");

  /**
   * Allows to provide a custom qualified name when a specific symbol is going to be imported into a specific file.
   *
   * @param symbol   the symbol being imported
   * @param qName    the real qualified name of the symbol being imported.
   * @param foothold the location where the symbol is being imported.
   * @return the qualified name to use in the import statement, or null if no replacement is necessary.
   */
  @Nullable
  QualifiedName getCanonicalPath(@Nullable PsiElement symbol, @NotNull QualifiedName qName, @Nullable PsiElement foothold);
}
