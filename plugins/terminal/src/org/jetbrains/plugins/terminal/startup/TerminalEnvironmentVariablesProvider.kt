// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.openapi.components.service
import com.intellij.platform.eel.EelApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.fetchMinimalEnvironmentVariables

@ApiStatus.Internal
interface TerminalEnvironmentVariablesProvider {
  suspend fun fetchMinimalEnvironmentVariableValue(eelApi: EelApi, envName: String): String?

  companion object {
    val instance: TerminalEnvironmentVariablesProvider
      get() = service<TerminalEnvironmentVariablesProvider>()
  }
}

internal class TerminalEnvironmentVariablesProviderImpl: TerminalEnvironmentVariablesProvider {
  override suspend fun fetchMinimalEnvironmentVariableValue(
    eelApi: EelApi,
    envName: String,
  ): String? {
    return eelApi.fetchMinimalEnvironmentVariables()[envName]
  }
}
