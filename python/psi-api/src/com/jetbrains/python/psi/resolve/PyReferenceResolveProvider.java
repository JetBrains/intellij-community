// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.jetbrains.python.psi.PyQualifiedElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 *
 * User : ktisha
 */
public interface PyReferenceResolveProvider {
  ExtensionPointName<PyReferenceResolveProvider> EP_NAME = ExtensionPointName.create("Pythonid.pyReferenceResolveProvider");

  /**
   * Allows to provide a custom resolve result for qualified expression
   * @deprecated This method will be removed in 2019.1.
   */
  @Deprecated
  @NotNull
  List<RatedResolveResult> resolveName(@NotNull PyQualifiedExpression element, @NotNull TypeEvalContext context);

  /**
   * Allows to provide a custom resolve result for qualified element
   * @apiNote This method will be marked as abstract in 2019.1.
   */
  @NotNull
  default List<RatedResolveResult> resolveName(@NotNull PyQualifiedElement element, @NotNull TypeEvalContext context) {
    return element instanceof PyQualifiedExpression ? resolveName((PyQualifiedExpression)element, context) : Collections.emptyList();
  }

  /**
   * @deprecated This method will be removed in 2019.1.
   */
  @Deprecated
  default boolean allowsForwardOutgoingReferencesInClass(@NotNull PyQualifiedExpression element) {
    return false;
  }

  default boolean allowsForwardOutgoingReferencesInClass(@NotNull PyQualifiedElement element) {
    return element instanceof PyQualifiedExpression && allowsForwardOutgoingReferencesInClass((PyQualifiedExpression)element);
  }
}
