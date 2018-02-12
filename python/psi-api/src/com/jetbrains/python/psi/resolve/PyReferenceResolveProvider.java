// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
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
  List<RatedResolveResult> resolveName(@NotNull PyQualifiedExpression element, @NotNull TypeEvalContext context);

  default boolean allowsForwardOutgoingReferencesInClass(@NotNull PyQualifiedExpression element) {
    return false;
  }
}
