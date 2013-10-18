package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 *
 * User : ktisha
 */
public interface PyReferenceResolveProvider {
  ExtensionPointName<PyReferenceResolveProvider> EP_NAME = ExtensionPointName.create("Pythonid.pyReferenceResolveProvider");

  /**
   * Allows to provide a custom resolve result for qualified expression
   */
  @NotNull
  List<RatedResolveResult> resolveName(@NotNull final PyQualifiedExpression element,
                                       @NotNull final List<PsiElement> definers);
}
