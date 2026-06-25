// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

/**
 * @deprecated The collection-specific accessors have been folded into {@link PyClassType}
 * ({@link PyClassType#getTypeArguments()}, {@link PyClassType#getIteratedItemType()}), so this interface no longer
 * carries any members of its own. It is preserved solely as an empty marker so that {@code instanceof PyCollectionType}
 * checks in third-party plugins keep working. Use {@link PyClassType} (and {@link PyClassType#isParameterized()}) instead.
 */
@Deprecated
public interface PyCollectionType extends PyClassType {
}
