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
  fun `plugin directory is the directory of the plugin in the pool`() {
    val manifest = writeSamplePool()
    val pool = ContextPluginDirs.read(manifest)

    val directory = pool.pluginDirectory("devkit")

    assertEquals(manifest.resolveSibling("pool/devkit"), directory)
    assertEquals("devkit/lib/devkit.jar", directory.resolve("lib/devkit.jar").readText())
    assertEquals(manifest.resolveSibling("pool/lambda-test-plugin"), pool.pluginDirectory("lambda-test-plugin"))
  }

  @Test
  fun `plugin directory accepts a directory tree and does not look into it`() {
    val manifest = writeSamplePool()
    val packed = root.resolve("packed-tree/native/mac").createDirectories()
    packed.resolve("libfoo.dylib").writeText("libfoo")
    // A `declare_directory` output of Bazel holds links to the packed files and directories.
    val tree = manifest.resolveSibling("pool/devkit/native").createDirectories()
    link(tree.resolve("mac"), packed)
    link(tree.resolve("readme.txt"), root.resolve("packed-tree/readme.txt").apply { writeText("readme") })
    manifest.writeText(manifest.readText() + "devkit/native/\tpool/devkit/native/\n")

    val directory = ContextPluginDirs.read(manifest).pluginDirectory("devkit")

    assertEquals(manifest.resolveSibling("pool/devkit"), directory)
    assertEquals("libfoo", directory.resolve("native/mac/libfoo.dylib").readText())
  }

  @Test
  fun `plugin directory accepts a link to a directory tree`() {
    val manifest = writeSamplePool()
    val packed = root.resolve("packed-tree/native").createDirectories()
    packed.resolve("libfoo.dylib").writeText("libfoo")
    // A runfiles tree links a tree output as a whole. On Windows the link is a copy, a real directory.
    link(manifest.resolveSibling("pool/devkit/native"), packed)
    manifest.writeText(manifest.readText() + "devkit/native/\tpool/devkit/native/\n")

    val directory = ContextPluginDirs.read(manifest).pluginDirectory("devkit")

    assertEquals("libfoo", directory.resolve("native/libfoo.dylib").readText())
  }

  @Test
  fun `plugin directory with a file that the manifest does not name fails`() {
    val manifest = writeSamplePool()
    manifest.resolveSibling("pool/devkit/lib/stale.jar").writeText("stale")
    val pool = ContextPluginDirs.read(manifest)

    val error = assertThrows<IllegalStateException> { pool.pluginDirectory("devkit") }

    assertTrue(error.message!!.contains("stale.jar") && error.message!!.contains("do not name"), error.message)
    assertEquals(manifest.resolveSibling("pool/lambda-test-plugin"), pool.pluginDirectory("lambda-test-plugin"))
  }

  @Test
  fun `plugin directory with a directory that the manifest does not name fails`() {
    val manifest = writeSamplePool()
    manifest.resolveSibling("pool/devkit/old").createDirectories()

    val error = assertThrows<IllegalStateException> { ContextPluginDirs.read(manifest).pluginDirectory("devkit") }

    assertTrue(error.message!!.contains("old") && error.message!!.contains("do not name"), error.message)
  }

  @Test
  fun `plugin directory that two places hold fails`() {
    val manifest = writeSamplePool()
    val value = "_main/tests/${manifest.fileName}"
    val moved = "pool/devkit/lib/modules/intellij.devkit.core.jar"
    val other = root.resolve("other")
    other.resolve("_main/tests/$moved").apply { parent.createDirectories() }.writeText("core")
    Files.delete(manifest.resolveSibling(moved))
    val pool = ContextPluginDirs.resolve(value) { path -> if (path == value) manifest else other.resolve(path) }

    val error = assertThrows<IllegalStateException> { pool.pluginDirectory("devkit") }

    assertTrue(error.message!!.contains("in two directories"), error.message)
  }

  @Test
  fun `plugin directory with a directory tree resolves only from the runfiles manifest`() {
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

    val directory = pool.pluginDirectory("devkit")

    assertEquals(manifest.resolveSibling("pool/devkit"), directory)
    assertEquals("libfoo", directory.resolve("native/mac/libfoo.dylib").readText())
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
    assertEquals(manifest.resolveSibling("pool/devkit"), pool.pluginDirectory("devkit"))
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
