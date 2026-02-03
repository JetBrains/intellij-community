// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.google.gson.annotations.JsonAdapter
import com.intellij.execution.target.FullPathOnTarget
import com.jetbrains.python.sdk.SealedClassAdapter
import org.jetbrains.annotations.ApiStatus


/**
 * Conda environment could be either named or unnamed (based on path).
 * [userReadableName] used as sdk name
 */
@ApiStatus.Internal
@JsonAdapter(SealedClassAdapter::class)
sealed class PyCondaEnvIdentity(val userReadableName: String) {

  data class NamedEnv(val envName: String) : PyCondaEnvIdentity(envName) {
    override fun toString(): String = envName
  }

  data class UnnamedEnv(val envPath: FullPathOnTarget, val isBase: Boolean) : PyCondaEnvIdentity(envPath) {
    override fun toString(): String = envPath
  }
}