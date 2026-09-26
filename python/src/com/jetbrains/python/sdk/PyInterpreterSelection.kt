// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus

/**
 * What the "Python Interpreter" settings panel has selected, for a [PyCustomSdkUiProvider] that adds a control to it.
 *
 * A provider is handed this rather than the panel's combo box. That combo holds a heterogeneous list — interpreters,
 * separators, the "Show All" row, and `null` for "no interpreter" — so reading its selection means knowing which of
 * those a value is. That is the panel's own business: a provider in another module used to cast the value to [Sdk] and
 * threw a ClassCastException the moment the list stopped holding SDKs (PY-91967).
 */
@ApiStatus.Internal
interface PyInterpreterSelection {
  /** The selected interpreter, or `null` when the panel has none selected. */
  val selectedSdk: Sdk?

  /** Runs [listener] on the EDT whenever the selection changes. */
  fun onChange(listener: Runnable)
}
