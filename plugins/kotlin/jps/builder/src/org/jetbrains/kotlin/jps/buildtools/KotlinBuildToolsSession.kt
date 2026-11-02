// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.buildtools

import org.jetbrains.kotlin.buildtools.api.KotlinToolchains

/**
 * The [KotlinToolchains.BuildSession] of one JPS build.
 *
 * The session is created on the first Kotlin chunk, so that a build without Kotlin never loads the implementation.
 * `KotlinBuilder.buildFinished` closes it.
 */
class KotlinBuildToolsSession internal constructor() {
  private var session: KotlinToolchains.BuildSession? = null

  /**
   * @return the session, or `null` when the IDE handed over no implementation.
   */
  @Synchronized
  fun getOrCreate(): KotlinToolchains.BuildSession? {
    session?.let { return it }
    val toolchains = KotlinBuildToolsLoader.load() ?: return null
    return toolchains.createBuildSession().also { session = it }
  }

  @Synchronized
  fun close() {
    session?.close()
    session = null
  }
}
