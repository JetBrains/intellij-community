// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
@ApiStatus.Experimental
interface MavenSyncSpec {
  fun forceReading(): Boolean

  fun resolveIncrementally(): Boolean

  val isExplicit: Boolean

  companion object {
    @JvmStatic
    @JvmOverloads
    fun incremental(description: String, explicit: Boolean = false): MavenSyncSpec {
      return MavenSyncSpecImpl(true, explicit, description)
    }

    @JvmStatic
    @JvmOverloads
    fun full(description: String, explicit: Boolean = false): MavenSyncSpec {
      return MavenSyncSpecImpl(false, explicit, description)
    }
  }
}

internal class MavenSyncSpecImpl(
  private val incremental: Boolean,
  override val isExplicit: Boolean,
  private val description: String
) : MavenSyncSpec {
  override fun forceReading(): Boolean {
    return !incremental
  }

  override fun resolveIncrementally(): Boolean {
    return incremental && Registry.`is`("maven.incremental.sync.resolve.dependencies.incrementally")
  }

  override fun toString(): String {
    return "incremental=$incremental, $description"
  }
}
