// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.target

import com.intellij.ui.dsl.builder.Panel
import com.jetbrains.python.target.PyTargetAwareAdditionalData

/**
 * Allows extending the target configuration with additional options.
 *
 * The class is stateful.
 */
interface TargetPanelExtension {
  fun extendDialogPanelWithOptionalFields(panel: Panel)

  fun applyToTargetConfiguration()

  fun applyToAdditionalData(pyTargetAwareAdditionalData: PyTargetAwareAdditionalData)
}
