// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.conda.execution.models

import com.intellij.openapi.util.IntellijInternalApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@IntellijInternalApi
@Serializable
data class CondaEnvInfo(
  @SerialName("envs")
  val envs: List<String>,
  @SerialName("envs_dirs")
  val envsDirs: List<String> = emptyList(),
  @SerialName("conda_prefix")
  val condaPrefix: String? = null,
  @SerialName("envs_details")
  val envsDetails: Map<String, CondaEnvDetail>? = null,
)

@ApiStatus.Internal
@IntellijInternalApi
@Serializable
data class CondaEnvDetail(
  @SerialName("name")
  val name: String,
  @SerialName("base")
  val base: Boolean,
)
