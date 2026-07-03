// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelApi

/**
 * Creates a [PyToolManager] for a given environment, contributed per backend (e.g. uv, pip) through
 * [EP_NAME]. Providers are consulted in registration order; the first one able to operate in the
 * target environment wins.
 *
 * Implementations live in higher-level modules (uv backend, the main python impl) so `python-pytools`
 * does not need to depend on them.
 */
interface PyToolManagerProvider {
  /**
   * A [PyToolManager] bound to [eel], or `null` when this provider cannot operate there — e.g. its
   * backing tool (uv on PATH, a system Python, …) is not present.
   */
  suspend fun forEel(eel: EelApi): PyToolManager?

  companion object {
    val EP_NAME: ExtensionPointName<PyToolManagerProvider> =
      ExtensionPointName.create("com.intellij.python.pytools.pyToolManagerProvider")

    /** The [PyToolManager] from the highest-priority provider that can operate in [eel], or `null`. */
    suspend fun managerFor(eel: EelApi): PyToolManager? = EP_NAME.extensionList.firstNotNullOfOrNull { it.forEel(eel) }
  }
}
