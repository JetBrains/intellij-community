// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add.target

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.ui.layout.*

/**
 * Allows to extend the target configuration with additional synchronization options.
 *
 * The class is stateful.
 */
interface ProjectSync {
  fun extendDialogPanelWithOptionalFields(layoutBuilder: LayoutBuilder)

  fun apply(configuration: TargetEnvironmentConfiguration)
}