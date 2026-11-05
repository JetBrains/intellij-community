// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.google.gson.annotations.JsonAdapter
import com.intellij.execution.target.FullPathOnTarget
import com.jetbrains.python.PathShortener
import com.jetbrains.python.sdk.SealedClassAdapter
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.venvReader.Directory
import org.jetbrains.annotations.ApiStatus


/**
 * Conda environment could be either named or unnamed (based on path).
 * [userReadableName] used as sdk name
 */
@ApiStatus.Internal
@JsonAdapter(SealedClassAdapter::class)
sealed class PyCondaEnvIdentity(val userReadableName: String) {
  abstract val envPath: FullPathOnTarget?

  /**
   * @param envPath the directory the environment stands in, as conda reported it, or `null` when nothing reported one.
   *
   * conda addresses a named environment by its name, so the name is all a command needs. The directory is what lets a
   * caller read the environment from the file system instead of running conda over it, which is why it is kept.
   *
   * `null` has two causes, and neither is an error. An identity restored from an SDK saved before this field existed
   * carries no path, and so does one built from a request that only knows a name. A caller that needs the directory
   * must handle `null`.
   */
  data class NamedEnv(val envName: String, override val envPath: FullPathOnTarget? = null) : PyCondaEnvIdentity(envName) {
    override fun toString(): String = envName
  }

  data class UnnamedEnv(override val envPath: FullPathOnTarget, val isBase: Boolean) : PyCondaEnvIdentity(envPath) {
    companion object {
      suspend operator fun invoke(envDir: Directory, isBase: Boolean): UnnamedEnv = UnnamedEnv(PathShortener.shorten(envDir), isBase)
      suspend operator fun invoke(envDir: PathHolder, isBase: Boolean): UnnamedEnv =
        when (envDir) {
          is PathHolder.Eel -> invoke(envDir.path, isBase)
          is PathHolder.Target -> UnnamedEnv(envDir.toStringOnRemoteMachine(), isBase)
        }
    }

    override fun toString(): String = envPath
  }
}