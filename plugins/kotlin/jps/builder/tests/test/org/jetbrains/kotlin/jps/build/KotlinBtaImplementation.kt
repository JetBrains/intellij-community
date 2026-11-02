// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.idea.artifacts.KotlinJpsBuilderTestArtifacts
import org.jetbrains.kotlin.jps.buildtools.KotlinBuildToolsLoader
import java.io.File
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * A Build Tools API implementation that the Kotlin JPS builder tests compile with.
 *
 * The implementation is not on the test classpath. [install] hands its directory over to the builder, as the IDE hands
 * it over to the build process. Each implementation is `kotlin-build-tools-impl` with its run-time classpath, see
 * [KotlinJpsBuilderTestArtifacts.buildToolsImplHome].
 */
enum class KotlinBtaImplementation {
  /**
   * The implementation of [KotlinJpsBuilderTestArtifacts.BUILD_TOOLS_IMPL_CURRENT_VERSION].
   *
   * The `kotlin.jps.tests.build.tools.impl.home` system property or the `KOTLIN_JPS_BTA_IMPL_HOME` environment variable
   * replaces it with a local directory. An example is the `btaImplHome` output of
   * `:prepare:ide-plugin-dependencies:kotlin-build-tools-impl-for-ide` in the Kotlin repository.
   */
  CURRENT {
    override fun findHome(): Path {
      val local = System.getProperty(LOCAL_HOME_PROPERTY)?.takeIf { it.isNotBlank() }
                  ?: System.getenv(LOCAL_HOME_ENVIRONMENT_VARIABLE)?.takeIf { it.isNotBlank() }
      return local?.let { Path.of(it) } ?: KotlinJpsBuilderTestArtifacts.buildToolsImplHome(KotlinJpsBuilderTestArtifacts.BUILD_TOOLS_IMPL_CURRENT_VERSION)
    }
  },

  /** The implementation of [KotlinJpsBuilderTestArtifacts.BUILD_TOOLS_IMPL_STABLE_VERSION]. */
  STABLE {
    override fun findHome(): Path = KotlinJpsBuilderTestArtifacts.buildToolsImplHome(KotlinJpsBuilderTestArtifacts.BUILD_TOOLS_IMPL_STABLE_VERSION)
  };

  internal abstract fun findHome(): Path

  /** The directory with the implementation jars. */
  val home: Path by lazy { findHome() }

  /** The version of the implementation jar in [home]. */
  val version: String by lazy {
    home.listDirectoryEntries("*.jar").firstNotNullOfOrNull { IMPLEMENTATION_JAR.matchEntire(it.name)?.groupValues?.get(1) }
    ?: error("No kotlin-build-tools-impl jar in $home")
  }

  /**
   * Whether the tests of this implementation repeat the tests of [CURRENT]. It is so when both versions are the same, so
   * the tests skip such an implementation.
   */
  val duplicatesCurrent: Boolean
    get() = this != CURRENT && version == CURRENT.version

  /** The compiler version that the implementation reports. */
  @OptIn(ExperimentalBuildToolsApi::class)
  val compilerVersion: String by lazy {
    install()
    val toolchains = KotlinBuildToolsLoader.load() ?: error("No Build Tools API implementation in $home")
    toolchains.getCompilerVersion()
  }

  /**
   * The standard library of the implementation and its annotations, for the classpath of a test project.
   *
   * The library comes from [home], so that its version matches the compiler.
   */
  val stdlibRoots: List<File> by lazy {
    val jars = home.listDirectoryEntries("*.jar")
    val stdlib = jars.firstOrNull { STDLIB_JAR.matches(it.name) } ?: error("No kotlin-stdlib in the Build Tools API implementation $home")
    listOfNotNull(stdlib, jars.firstOrNull { ANNOTATIONS_JAR.matches(it.name) }).map { it.toFile() }
  }

  /** The `kotlin-test` library of the version of the implementation, for the classpath of a test project. */
  val kotlinTestRoots: List<File> by lazy { listOf(KotlinJpsBuilderTestArtifacts.kotlinTest(version).toFile()) }

  /** Hands [home] over to the builder, as the IDE does for the build process. */
  fun install() {
    System.setProperty(KotlinBuildToolsLoader.IMPL_HOME_PROPERTY, home.toString())
  }

  private companion object {
    const val LOCAL_HOME_PROPERTY: String = "kotlin.jps.tests.build.tools.impl.home"
    const val LOCAL_HOME_ENVIRONMENT_VARIABLE: String = "KOTLIN_JPS_BTA_IMPL_HOME"

    val IMPLEMENTATION_JAR = Regex("kotlin-build-tools-impl-(\\d.*)\\.jar")
    val STDLIB_JAR = Regex("kotlin-stdlib(-\\d.*)?\\.jar")
    val ANNOTATIONS_JAR = Regex("annotations-13\\.0\\.jar")
  }
}
