// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.target

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.ui.dsl.builder.Panel

/**
 * Allows to extend the target configuration with additional synchronization options.
 *
 * The class is stateful.
 */
interface ProjectSync {
  @Deprecated("Use extendDialogPanelWithOptionalFields(Panel, TargetEnvironmentConfiguration)")
  fun extendDialogPanelWithOptionalFields(panel: Panel): Unit = throw UnsupportedOperationException()
  fun extendDialogPanelWithOptionalFields(panel: Panel, targetEnvConf: TargetEnvironmentConfiguration): Unit =
    extendDialogPanelWithOptionalFields(panel)

  fun apply(configuration: TargetEnvironmentConfiguration)
}