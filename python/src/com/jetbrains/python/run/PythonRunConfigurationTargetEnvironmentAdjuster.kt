// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run

import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus

/**
 * Allows to configure the request for creating the target environment based on
 * the settings of the corresponding Python Run Configuration.
 */
@ApiStatus.Experimental
interface PythonRunConfigurationTargetEnvironmentAdjuster {
  /**
   * Adjusts [targetEnvironmentRequest] using the settings from
   * [runConfiguration].
   *
   * Note that [AbstractPythonRunConfiguration.getSdk] is expected to match SDK
   * that was used for finding the current adjuster using [isEnabledFor] or the
   * utility method [findTargetEnvironmentRequestAdjuster].
   */
  fun adjust(targetEnvironmentRequest: TargetEnvironmentRequest, runConfiguration: AbstractPythonRunConfiguration<*>)

  fun isEnabledFor(sdk: Sdk): Boolean

  companion object {
    private val LOG = logger<PythonRunConfigurationTargetEnvironmentAdjuster>()

    @JvmStatic
    val EP_NAME = ExtensionPointName<PythonRunConfigurationTargetEnvironmentAdjuster>("Pythonid.runConfigurationTargetEnvironmentAdjuster")

    @JvmStatic
    fun findTargetEnvironmentRequestAdjuster(sdk: Sdk): PythonRunConfigurationTargetEnvironmentAdjuster? {
      val filter = EP_NAME.extensionList.filter { it.isEnabledFor(sdk) }
      if (filter.size > 1) {
        LOG.warn("Several PythonRunConfigurationTargetEnvironmentSettings EPs suitable for SDK '$sdk' found." +
                 " Only the first one will be applied.")
      }
      return filter.firstOrNull()
    }
  }
}