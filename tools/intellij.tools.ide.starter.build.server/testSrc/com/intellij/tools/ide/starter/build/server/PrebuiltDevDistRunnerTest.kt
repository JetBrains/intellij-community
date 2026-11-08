// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.build.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories

class PrebuiltDevDistRunnerTest {
  @TempDir
  lateinit var root: Path

  @Test
  fun `each IDE of a split run gets the distribution of its product`() {
    val declared = listOf("idea", "ideaJetBrainsClient")

    assertEquals(0, declaredDevDistFor(declared, platformPrefix = "idea", baseIdePlatformPrefixForFrontend = null))
    assertEquals(1, declaredDevDistFor(declared, platformPrefix = "JetBrainsClient", baseIdePlatformPrefixForFrontend = "idea"))
  }

  @Test
  fun `a frontend without a declared distribution of its product gets none`() {
    assertNull(declaredDevDistFor(listOf("idea"), platformPrefix = "JetBrainsClient", baseIdePlatformPrefixForFrontend = "idea"))
    assertNull(declaredDevDistFor(listOf("idea", "RiderJetBrainsClient"), platformPrefix = "JetBrainsClient", baseIdePlatformPrefixForFrontend = "idea"))
  }

  @Test
  fun `requested module of the pool is available`() {
    val pool = ContextPluginDirs.read(writeContextPluginPool(root))
    val requested = listOf("intellij.java.plugin", "intellij.devkit", "intellij.lambda.test.plugin", "intellij.go")

    assertEquals(listOf("intellij.go"), unavailableDevDistModules(requested, declared = emptyList(), bundled = listOf("intellij.java.plugin"), pool))
    assertEquals(
      listOf("intellij.devkit", "intellij.go", "intellij.lambda.test.plugin"),
      unavailableDevDistModules(requested, declared = emptyList(), bundled = listOf("intellij.java.plugin"), contextPlugins = null),
    )
  }

  @Test
  fun `context plugin paths name only the plugins of the missing modules`() {
    val manifest = writeContextPluginPool(root)
    val pool = ContextPluginDirs.read(manifest)
    val homePluginsDir = root.resolve("home/plugins").createDirectories()

    assertEquals(
      listOf(manifest.resolveSibling("pool/lambda-test-plugin")),
      contextPluginPaths(listOf("intellij.lambda.test.plugin"), pool, homePluginsDir),
    )
    assertEquals(emptyList<Path>(), contextPluginPaths(emptyList(), pool, homePluginsDir))
  }

  @Test
  fun `context plugin paths fail on a plugin directory of the distribution`() {
    val pool = ContextPluginDirs.read(writeContextPluginPool(root))
    val homePluginsDir = root.resolve("home/plugins")
    homePluginsDir.resolve("devkit").createDirectories()

    val error = assertThrows<IllegalStateException> {
      contextPluginPaths(listOf("intellij.devkit"), pool, homePluginsDir)
    }
    assertTrue(error.message!!.contains("'devkit'"), error.message)
  }

  @Test
  fun `context plugin paths fail on a module that the pool does not have`() {
    val pool = ContextPluginDirs.read(writeContextPluginPool(root))
    val error = assertThrows<IllegalStateException> {
      contextPluginPaths(listOf("intellij.go"), pool, root.resolve("home/plugins"))
    }
    assertTrue(error.message!!.contains("'intellij.go'"), error.message)
  }
}
