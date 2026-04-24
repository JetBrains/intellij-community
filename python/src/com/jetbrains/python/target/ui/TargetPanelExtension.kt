// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.target.ui

import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus

/**
 * Allows extending the target configuration with additional options.
 *
 * The class is stateful.
 */
@ApiStatus.Internal
interface TargetPanelExtension {
  fun extendDialogPanelWithOptionalFields(panel: Panel)

  fun applyToTargetConfiguration()

  fun applyToAdditionalData(pyTargetAwareAdditionalData: PyTargetAwareAdditionalData)

  /**
   * When [required] is `true`, forces auto-upload of project files and prevents the user from disabling it.
   * Used by environment managers (e.g., uv) that require project files to be present on the remote host.
   */
  fun setAutoUploadRequired(required: Boolean) {
    // default no-op for extensions that don't have an auto-upload control
  }
}
