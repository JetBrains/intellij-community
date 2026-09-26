// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.shellDetection

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class ShellsDetectionResult(
  /** Environments where shells can be started */
  val environments: List<DetectedShellsEnvironmentInfo>,
)

@ApiStatus.Internal
@Serializable
data class DetectedShellsEnvironmentInfo(
  /** User-readable name of the environment where shell can be started */
  val environmentName: @NlsSafe String,
  val shells: List<DetectedShellInfo>,
)

@ApiStatus.Internal
@Serializable
data class DetectedShellInfo(
  /** User-readable name of the shell, for example, zsh or Windows PowerShell */
  val name: @NlsSafe String,
  /** Absolute path of the shell executable, local to the [eelDescriptor] */
  val path: String,
  /** Additional command line options that should be used to start this shell */
  val options: List<String> = emptyList(),
  @Transient
  /** Descriptor of the environment where shell can be started */
  val eelDescriptor: EelDescriptor = LocalEelDescriptor,
)