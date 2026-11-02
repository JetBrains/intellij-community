// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.buildtools

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import java.io.File
import java.nio.file.Path

/**
 * Loads the Build Tools API implementation of the project's Kotlin version.
 *
 * The API ships with the IDE, the implementation follows the project. The IDE resolves the implementation and hands
 * over a directory with its jars in the [IMPL_HOME_PROPERTY] system property of the build process. A directory, not a
 * classpath, because a build process on another host gets only path parameters translated.
 *
 * [KotlinToolchains.loadImplementation] puts the implementation behind a `URLClassLoader` that shares only the
 * `org.jetbrains.kotlin.buildtools.api` packages with the builder. The implementation classpath is therefore
 * self-contained: it holds the embeddable compiler and its runtime.
 */
object KotlinBuildToolsLoader {
  /** The system property with the directory of the implementation jars. The IDE side uses the same name. */
  const val IMPL_HOME_PROPERTY: String = "kotlin.jps.build.tools.impl.home"

  /** The directory the IDE handed over, or `null` when it handed over none. */
  val implHomeProperty: String?
    get() = System.getProperty(IMPL_HOME_PROPERTY)

  private val LOG = Logger.getInstance(KotlinBuildToolsLoader::class.java)

  private var cached: Pair<List<Path>, KotlinToolchains>? = null

  /**
   * @return the jars of the implementation directory, or `null` when the IDE handed over nothing usable.
   */
  fun resolveClasspath(implDirectory: String? = implHomeProperty): List<Path>? =
    File(implDirectory ?: return null)
      .listFiles()
      .orEmpty()
      .filter { it.isFile && it.name.endsWith(".jar") }
      .sortedBy { it.name }
      .map { it.toPath() }
      .takeIf { it.isNotEmpty() }

  /**
   * Loads the implementation, or returns the loaded one when the directory did not change.
   *
   * The loaded [KotlinToolchains] lives as long as the build process. `BuildSession.close()` does not release the class
   * loader, and a build process serves many builds.
   */
  @Synchronized
  fun load(): KotlinToolchains? {
    val classpath = resolveClasspath()
    if (classpath == null) {
      LOG.info("No Build Tools API implementation: '$IMPL_HOME_PROPERTY' is not set, or its directory holds no jars")
      return null
    }
    cached?.let { if (it.first == classpath) return it.second }

    val toolchains = KotlinToolchains.loadImplementation(classpath)
    LOG.info(
      "Loaded the Build Tools API implementation ${toolchains.getCompilerVersion()} from ${classpath.size} jars" +
      " (API ${KotlinToolchains.getVersion()}, builder compiled against Kotlin ${KotlinCompilerVersion.VERSION})"
    )
    cached = classpath to toolchains
    return toolchains
  }
}
