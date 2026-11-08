// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.build.server

import com.intellij.platform.devIdeConfig.DevIdeConfig
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
  fun `copy of a plugin directory copies a directory tree with the links resolved`() {
    val manifest = writeSamplePool()
    val packed = root.resolve("packed-tree/native/mac").createDirectories()
    packed.resolve("libfoo.dylib").writeText("libfoo")
    // A `declare_directory` output of Bazel holds links to the packed files and directories.
    val tree = manifest.resolveSibling("pool/devkit/native").createDirectories()
    link(tree.resolve("mac"), packed)
    link(tree.resolve("readme.txt"), root.resolve("packed-tree/readme.txt").apply { writeText("readme") })
    manifest.writeText(manifest.readText() + "devkit/native/\tpool/devkit/native/\n")

    val pool = ContextPluginDirs.read(manifest)
    val installed = pool.installPluginDirectory("devkit", root.resolve("context/plugins"))

    val library = installed.resolve("native/mac/libfoo.dylib")
    assertEquals("libfoo", library.readText())
    assertFalse(library.isSymbolicLink())
    assertFalse(library.parent.isSymbolicLink())
    assertTrue(library.parent.isWritable())
    assertEquals("readme", installed.resolve("native/readme.txt").readText())
    assertEquals("devkit/lib/devkit.jar", installed.resolve("lib/devkit.jar").readText())
  }

  @Test
  fun `pool installs a directory tree resolved only from the runfiles manifest`() {
    val manifest = writeSamplePool()
    val packed = root.resolve("packed-tree/native/mac").createDirectories()
    packed.resolve("libfoo.dylib").writeText("libfoo")
    val tree = manifest.resolveSibling("pool/devkit/native").createDirectories()
    link(tree.resolve("mac"), packed)
    link(tree.resolve("readme.txt"), root.resolve("packed-tree/readme.txt").apply { writeText("readme") })
    manifest.writeText(manifest.readText() + "devkit/native/\tpool/devkit/native/\n")

    val runfilesManifest = writeBazelRunfilesManifest(root, manifest)
    val value = "_main/tests/${manifest.fileName}"
    assertFalse(root.resolve("manifest-only/pool").exists())
    val pool = ContextPluginDirs.resolve(value) { runfile ->
      DevIdeConfig.findInRunfilesManifest(runfilesManifest, runfile) ?: throw IllegalStateException("'$runfile' names no runfile")
    }
    val installed = pool.installPluginDirectory("devkit", root.resolve("context/plugins"))

    val library = installed.resolve("native/mac/libfoo.dylib")
    assertEquals("libfoo", library.readText())
    assertFalse(library.isSymbolicLink())
    assertFalse(library.parent.isSymbolicLink())
    assertTrue(library.isWritable())
    assertTrue(library.parent.isWritable())
    assertEquals("readme", installed.resolve("native/readme.txt").readText())
    val jar = installed.resolve("lib/devkit.jar")
    assertEquals("devkit/lib/devkit.jar", jar.readText())
    assertFalse(jar.isSymbolicLink())
    assertTrue(jar.isWritable())
  }

  @Test
  fun `pool reports a tree with no runfiles manifest entry`() {
    val manifest = writeSamplePool()
    manifest.resolveSibling("pool/devkit/native").createDirectories()
    manifest.writeText(manifest.readText() + "devkit/native/\tpool/devkit/native/\n")

    val runfilesManifest = writeBazelRunfilesManifest(root, manifest)
    val value = "_main/tests/${manifest.fileName}"
    val error = assertThrows<IllegalStateException> {
      ContextPluginDirs.resolve(value) { runfile ->
        DevIdeConfig.findInRunfilesManifest(runfilesManifest, runfile) ?: throw IllegalStateException("'$runfile' names no runfile")
      }
    }

    assertTrue(error.message!!.contains("neither beside the manifest nor a runfile"), error.message)
  }

  @Test
  fun `tree line without a directory fails`() {
    val manifest = writeSamplePool()
    manifest.writeText(manifest.readText() + "devkit/lib/devkit.jar/\tpool/devkit/lib/devkit.jar/\n")
    val error = assertThrows<IllegalStateException> { ContextPluginDirs.read(manifest) }
    assertTrue(error.message!!.contains("directory tree"), error.message)
  }

  @Test
  fun `tree destination without a tree path fails`() {
    val manifest = writeSamplePool()
    manifest.writeText(manifest.readText() + "devkit/lib/\tpool/devkit/lib\n")
    val error = assertThrows<IllegalStateException> { ContextPluginDirs.read(manifest) }
    assertTrue(error.message!!.contains("'/' at the end of both"), error.message)
  }

  @Test
  fun `pool resolves an absolute manifest path`() {
    val manifest = writeSamplePool()
    assertEquals(setOf("devkit", "lambda-test-plugin"), ContextPluginDirs.resolve(manifest.toString()).directories)
  }

  @Test
  fun `pool of a runfiles path finds a file in the runfiles when it is missing beside the manifest`() {
    val manifest = writeSamplePool()
    val runfiles = root.resolve("runfiles")
    val value = runfiles.relativize(manifest).toString().replace('\\', '/')
    // The manifest resolves outside the runfiles tree, where it has no sibling.
    val alone = root.resolve("outside/_main/tests").createDirectories().resolve(manifest.fileName.toString())
    Files.copy(manifest, alone)
    val resolveRunfile = { path: String -> if (path == value) alone else runfiles.resolve(path) }

    val pool = ContextPluginDirs.resolve(value, resolveRunfile)

    assertEquals(alone, pool.manifest)
    assertEquals(mapOf("intellij.devkit" to "devkit", "intellij.lambda.test.plugin" to "lambda-test-plugin"), pool.modules)
    val installed = pool.installPluginDirectory("devkit", root.resolve("context/plugins"))
    assertEquals("devkit/lib/devkit.jar", installed.resolve("lib/devkit.jar").readText())
    assertThrows<IllegalStateException> { ContextPluginDirs.read(alone) }
  }

  @Test
  fun `pool of a runfiles path names the manifest when a file is in no place`() {
    val manifest = writeSamplePool()
    val alone = root.resolve("outside").createDirectories().resolve(manifest.fileName.toString())
    Files.copy(manifest, alone)
    val value = "_main/tests/pool.context-plugins.txt"
    val error = assertThrows<IllegalStateException> {
      ContextPluginDirs.resolve(value) { path -> if (path == value) alone else throw IllegalStateException("'$path' names no runfile") }
    }
    assertTrue(error.message!!.contains("neither beside the manifest nor a runfile"), error.message)
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

  private fun link(link: Path, target: Path) {
    if (System.getProperty("os.name").startsWith("Windows")) {
      target.toFile().copyRecursively(link.toFile())
    }
    else {
      Files.createSymbolicLink(link, target)
    }
  }
}
