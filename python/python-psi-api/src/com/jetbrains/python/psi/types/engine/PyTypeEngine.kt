// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types.engine

import com.intellij.openapi.util.Ref
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyTypeEngine {
  fun isSupportedForResolve(pyTypedElement: PyTypedElement): Boolean
  fun resolveType(pyTypedElement: PyTypedElement, isLibrary: Boolean): Ref<PyType?>?
}
