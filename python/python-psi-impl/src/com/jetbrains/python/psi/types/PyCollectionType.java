// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @deprecated The collection-specific accessors have been folded into {@link PyClassType}
 * ({@link PyClassType#getTypeArguments()}, {@link PyClassType#getIteratedItemType()}), so this interface no longer
 * carries any members of its own. It is preserved solely as an empty marker so that {@code instanceof PyCollectionType}
 * checks in third-party plugins keep working. Use {@link PyClassType} (and {@link PyClassType#isParameterized()}) instead.
 * For the element types of a tuple, use {@link PyTupleType#getElementTypes()}, which overrides {@link #getElementTypes()}.
 */
@Deprecated
public interface PyCollectionType extends PyClassType {
  @NotNull
  default List<PyType> getElementTypes() {
    return getTypeArguments();
  }
}
