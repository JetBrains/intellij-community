// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.google.gson.*
import com.google.gson.annotations.JsonAdapter
import com.jetbrains.python.FullPathOnTarget
import com.jetbrains.python.sdk.SealedClassAdapter


/**
 * Conda environment could be either named or unnamed (based on path).
 */
@JsonAdapter(SealedClassAdapter::class)
sealed class PyCondaEnvIdentity {
  data class NamedEnv(val envName: String) : PyCondaEnvIdentity() {
    override fun toString(): String = envName
  }

  data class UnnamedEnv(val envPath: FullPathOnTarget, val isBase: Boolean) : PyCondaEnvIdentity() {
    override fun toString(): String = envPath
  }
}