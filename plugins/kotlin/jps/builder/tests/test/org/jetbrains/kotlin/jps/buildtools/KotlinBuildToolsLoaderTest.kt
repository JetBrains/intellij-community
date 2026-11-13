// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.buildtools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Checks the classpath and version resolution of [KotlinBuildToolsLoader] over an implementation home. */
class KotlinBuildToolsLoaderTest {
  @Test
  fun testResolvesTheImplementationVersionFromTheClasspath(@TempDir home: Path) {
    Files.createFile(home.resolve("kotlin-stdlib-2.5.20-dev-3865.jar"))
    Files.createFile(home.resolve("kotlin-build-tools-impl-2.5.20-dev-3865.jar"))

    val classpath = KotlinBuildToolsLoader.resolveClasspath(home.toString())!!

    val jarNames = classpath.map { it.fileName.toString() }
    assertEquals(
      listOf("kotlin-build-tools-impl-2.5.20-dev-3865.jar", "kotlin-stdlib-2.5.20-dev-3865.jar"),
      jarNames,
    )
    assertEquals("2.5.20-dev-3865", KotlinBuildToolsLoader.resolveImplementationVersion(classpath))
  }

  @Test
  fun testResolvesNoImplementationVersionWithoutTheImplementationJar(@TempDir home: Path) {
    Files.createFile(home.resolve("kotlin-stdlib-2.5.20-dev-3865.jar"))

    val classpath = KotlinBuildToolsLoader.resolveClasspath(home.toString())!!

    assertNull(KotlinBuildToolsLoader.resolveImplementationVersion(classpath))
  }
}
