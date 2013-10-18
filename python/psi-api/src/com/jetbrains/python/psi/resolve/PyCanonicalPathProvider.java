package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to provide a custom qualified name when a specific symbol is going to be imported into a specific file.
 *
 * @author yole
 */
public interface PyCanonicalPathProvider {
  ExtensionPointName<PyCanonicalPathProvider> EP_NAME = ExtensionPointName.create("Pythonid.canonicalPathProvider");

  /**
   * Allows to provide a custom qualified name when a specific symbol is going to be imported into a specific file.
   *
   * @param qName    the real qualified name of the symbol being imported.
   * @param foothold the location where the symbol is being imported.
   * @return the qualified name to use in the import statement, or null if no replacement is necessary.
   */
  @Nullable
  QualifiedName getCanonicalPath(@NotNull QualifiedName qName, @Nullable PsiElement foothold);
}
