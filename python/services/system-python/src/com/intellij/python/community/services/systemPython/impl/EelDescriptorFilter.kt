// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelDescriptor

/**
 * Control various parameters for [com.intellij.python.community.services.systemPython.SystemPythonServiceImpl] based
 * on [EelDescriptor], **not** a public SPI
 */
interface EelDescriptorFilter {

  companion object {
    internal val EP: ExtensionPointName<EelDescriptorFilter> =
      ExtensionPointName("com.intellij.python.community.services.systemPython.impl.eelFilter")
    internal val EelDescriptor.isEphemeral: Boolean get() = EP.extensionList.any { it.isEphemeral(this) }
  }

  /**
   * Ephemeral [eelDescriptor] should never be cached nor persisted in user settings
   */
  fun isEphemeral(eelDescriptor: EelDescriptor): Boolean
}
