// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.build.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.isWritable
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal class ContextPluginDirsTest {
  @TempDir
  lateinit var root: Path

  @Test
  fun `pool maps the main module of each plugin to its directory`() {
    val pool = ContextPluginDirs.read(writeSamplePool())

    assertEquals(setOf("devkit", "lambda-test-plugin"), pool.directories)
    assertEquals(mapOf("intellij.devkit" to "devkit", "intellij.lambda.test.plugin" to "lambda-test-plugin"), pool.modules)
    assertEquals("devkit", pool.directoryOf("intellij.devkit"))
    assertNull(pool.directoryOf("intellij.devkit.core"))
  }

  @Test
  fun `copy of a plugin directory resolves the links and replaces the old directory`() {
    val pool = ContextPluginDirs.read(writeSamplePool())
    val pluginsDir = root.resolve("context/plugins")
    val stale = pluginsDir.resolve("devkit/lib/stale.jar")
    stale.parent.createDirectories()
    stale.writeText("stale")

    val installed = pool.installPluginDirectory("devkit", pluginsDir)

    assertEquals(pluginsDir.resolve("devkit"), installed)
    assertFalse(stale.exists())
    val jar = installed.resolve("lib/devkit.jar")
    assertEquals("devkit/lib/devkit.jar", jar.readText())
    assertFalse(jar.isSymbolicLink())
    assertTrue(jar.isWritable())
    assertTrue(installed.resolve("lib/modules/intellij.devkit.core.jar").exists())
    assertFalse(pluginsDir.resolve("lambda-test-plugin").exists())
  }

  @Test
  fun `pool resolves an absolute manifest path`() {
    val manifest = writeSamplePool()
    assertEquals(setOf("devkit", "lambda-test-plugin"), ContextPluginDirs.resolve(manifest.toString()).directories)
  }

  @Test
  fun `pool without a module index fails`() {
    val manifest = writeSamplePool()
    Files.delete(manifest.resolveSibling("pool.context-plugin-modules.txt"))
    val error = assertThrows<IllegalStateException> { ContextPluginDirs.read(manifest) }
    assertTrue(error.message!!.contains("pool.context-plugin-modules.txt"), error.message)
  }

  @Test
  fun `module index that names an unknown directory fails`() {
    val manifest = writeSamplePool()
    manifest.resolveSibling("pool.context-plugin-modules.txt").writeText("intellij.go\tgo\n")
    val error = assertThrows<IllegalStateException> { ContextPluginDirs.read(manifest) }
    assertTrue(error.message!!.contains("'go'"), error.message)
  }

  private fun writeSamplePool(): Path = writeContextPluginPool(root)
}
