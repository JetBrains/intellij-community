// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.util.SystemProperties
import com.intellij.util.text.nullize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

interface ProjectLocationContext {
  val targetEnvironmentConfiguration: TargetEnvironmentConfiguration?

  suspend fun fetchUserHomeDirectory(): Path?
}

internal data object LocalContext : ProjectLocationContext {
  override val targetEnvironmentConfiguration: TargetEnvironmentConfiguration? = null

  override suspend fun fetchUserHomeDirectory(): Path? = SystemProperties.getUserHome().nullize(nullizeSpaces = true)?.let { Path.of(it) }
}

internal data class WslContext(val distribution: WSLDistribution) : ProjectLocationContext {
  override val targetEnvironmentConfiguration: TargetEnvironmentConfiguration = WslTargetEnvironmentConfiguration(distribution)

  override suspend fun fetchUserHomeDirectory(): Path? =
    withContext(Dispatchers.IO) {
      val userHome = distribution.userHome
      userHome?.let { distribution.getWindowsPath(userHome) }?.let { Path.of(it) }
    }
}