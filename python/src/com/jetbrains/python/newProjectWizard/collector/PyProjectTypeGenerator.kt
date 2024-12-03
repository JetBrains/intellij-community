// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard.collector

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

/**
 * [com.intellij.platform.DirectoryProjectGenerator] for something python-specific.
 * You must implement both [com.intellij.platform.DirectoryProjectGenerator] and this interface.
 *
 * This interface is a part of internal JetBrains infrastructure.
 * Extend [com.jetbrains.python.newProjectWizard.PyV3ProjectBaseGenerator] instead.
 */
@ApiStatus.Internal
interface PyProjectTypeGenerator {
  /**
   * Project type for FUS, see [PythonNewProjectWizardCollector.GENERATOR_FIELD].
   * Try not to change it ofter not to break statistics.
   *
   * This property is for JetBrains plugins only and will be ignored for other plugins.
   * Do not overwrite it if you aren't JetBrains employee.
   */
  val projectTypeForStatistics: @NlsSafe String
}