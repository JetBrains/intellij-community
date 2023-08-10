// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.target

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.remote.RemoteConnectionCredentialsWrapper
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ConnectionCredentialsToTargetConfigurationConverter {
  fun tryConvert(connectionCredentials: RemoteConnectionCredentialsWrapper): TargetEnvironmentConfiguration?

  companion object {
    val EP_NAME = ExtensionPointName.create<ConnectionCredentialsToTargetConfigurationConverter>(
      "Pythonid.connectionCredentialsToTargetConfigurationConverter"
    )
  }
}