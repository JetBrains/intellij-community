// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types.engine

import com.intellij.openapi.util.Ref
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyTypeEngine {
  val name: String

  /**
   * Whether the engine answers a request now.
   *
   * An engine that runs a server exists before the server runs, and it returns false until then.
   */
  val isReady: Boolean
    get() = true

  /**
   * Whether the engine can see [pyTypedElement] at all.
   *
   * The built-in engine answers for an element this engine cannot see.
   */
  fun isSupportedForResolve(pyTypedElement: PyTypedElement): Boolean
  fun resolveType(pyTypedElement: PyTypedElement, isUserInitiated: Boolean): Ref<PyType?>?
}
