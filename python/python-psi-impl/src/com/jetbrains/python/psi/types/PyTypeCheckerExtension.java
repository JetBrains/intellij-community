// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Extension to make additional check of {@link PyType} in {@link PyTypeChecker}
 */
@ApiStatus.Experimental
public interface PyTypeCheckerExtension {

  ExtensionPointName<PyTypeCheckerExtension> EP_NAME = ExtensionPointName.create("Pythonid.typeCheckerExtension");

  default @NotNull Optional<Boolean> match(@Nullable PyType expected,
                                           @Nullable PyType actual,
                                           @NotNull TypeEvalContext context,
                                           @NotNull PyTypeChecker.GenericSubstitutions substitutions) {
    Map<PyGenericType, PyType> legacyTypeVarSubs = new HashMap<>();
    for (Map.Entry<PyTypeVarType, PyType> entry : substitutions.getTypeVars().entrySet()) {
      if (entry.getKey() instanceof PyGenericType legacyTypeVar) {
        legacyTypeVarSubs.put(legacyTypeVar, entry.getValue());
      }
    }
    return match(expected, actual, context, legacyTypeVarSubs);
  }

  /**
   * Behaviour the same as {@link PyTypeChecker#match(PyType, PyType, TypeEvalContext, Map)}
   *
   * @deprecated use {@link #match(PyType, PyType, TypeEvalContext, PyTypeChecker.GenericSubstitutions)}
   */
  @Deprecated(forRemoval = true)
  default @NotNull Optional<Boolean> match(@Nullable PyType expected,
                                  @Nullable PyType actual,
                                  @NotNull TypeEvalContext context,
                                  @NotNull Map<PyGenericType, PyType> substitutions) {
    throw new UnsupportedOperationException();
  }
}