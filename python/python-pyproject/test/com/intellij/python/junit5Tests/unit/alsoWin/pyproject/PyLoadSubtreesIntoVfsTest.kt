// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.platformBridge.loadSubtreesIntoVfs
import com.intellij.python.pyproject.model.internal.pyProjectToml.findPyProjectTomlFilesInIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.io.createDirectories
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createFile

/**
 * Tests `loadSubtreesIntoVfs`, which fills the VFS so the filename index can report a nested
 * `pyproject.toml` (PY-91841).
 *
 * The loader must skip a directory that the search rejects anyway, or the VFS takes thousands of files for
 * nothing. A skipped directory keeps no cached child, and that is how each case reads the result. Every
 * directory below holds a child on disk, so an empty set of cached children means "the loader stopped
 * here".
 *
 * The reading of a cached child never loads one. `findChild` does load, so a case walks down only through
 * a directory that the loader already read.
 */
@TestApplication
internal class PyLoadSubtreesIntoVfsTest {
  private val pathFixture = tempPathFixture()

  private lateinit var root: Path
  private lateinit var rootFile: VirtualFile

  @BeforeEach
  fun createTree() {
    root = pathFixture.get()
    // `.hidden` and `node_modules` carry a pruned name. `out` is pruned by the excluded paths of a caller.
    for (name in listOf("member", "out", ".hidden", "node_modules")) {
      root.resolve(name).resolve("nested").createDirectories().resolve(PY_PROJECT_TOML).createFile()
    }
    // An environment with no dot in the name, so only the interpreter inside it can prune the directory.
    // Both layouts, because `VirtualEnvReader` reads `Scripts/python.exe` on Windows and `bin/python` on
    // posix. One layout alone makes this an ordinary directory on the other system.
    root.resolve("venv").apply {
      resolve("Scripts").createDirectories().resolve("python.exe").createFile()
      resolve("bin").createDirectories().resolve("python").createFile()
      resolve("nested").createDirectories().resolve(PY_PROJECT_TOML).createFile()
    }
    // The VFS learns the children of the root and nothing below them, which is the state after a VFS event.
    rootFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(root)!!
    rootFile.refresh(false, false)
  }

  private fun child(vararg names: String): VirtualFile {
    var file = rootFile
    for (name in names) file = file.findChild(name) ?: error("the VFS has no ${names.joinToString("/")}")
    return file
  }

  /** True when the loader read the children of [names], which is what a prune prevents. */
  private fun descendedInto(vararg names: String): Boolean =
    (child(*names) as NewVirtualFile).cachedChildren.isNotEmpty()

  @Test
  fun testTheLoaderDescendsIntoAnOrdinaryDirectory(): Unit = timeoutRunBlocking {
    loadSubtreesIntoVfs(setOf(rootFile))
    assertThat(descendedInto("member")).describedAs("an ordinary directory").isTrue()
    assertThat(descendedInto("member", "nested")).describedAs("its nested directory").isTrue()
  }

  @Test
  fun testTheLoaderSkipsAnExcludedDirectory(): Unit = timeoutRunBlocking {
    loadSubtreesIntoVfs(setOf(rootFile), excludedPaths = setOf(root.resolve("out")))
    assertThat(descendedInto("out")).describedAs("an excluded directory").isFalse()
    assertThat(descendedInto("member")).describedAs("the control").isTrue()
  }

  @Test
  fun testTheLoaderSkipsAPrunedName(): Unit = timeoutRunBlocking {
    loadSubtreesIntoVfs(setOf(rootFile))
    assertThat(descendedInto(".hidden")).describedAs("a dot directory").isFalse()
    assertThat(descendedInto("node_modules")).describedAs("a name of the prune list").isFalse()
  }

  /**
   * The loader reads the names of the children of a directory to find an interpreter, so the children of
   * the environment become known. The subtree below them is what the rule saves.
   */
  @Test
  fun testTheLoaderSkipsTheSubtreeOfAnEnvironment(): Unit = timeoutRunBlocking {
    loadSubtreesIntoVfs(setOf(rootFile))
    assertThat(descendedInto("venv")).describedAs("the names of an environment are read").isTrue()
    assertThat(descendedInto("venv", "nested")).describedAs("its subtree stays unread").isFalse()
  }

  /** The load is what makes a nested file reach the index at all. */
  @Test
  fun testTheIndexReportsANestedFileOnlyAfterTheLoad(): Unit = timeoutRunBlocking {
    val nested = root.resolve("member").resolve("nested").resolve(PY_PROJECT_TOML)
    assertThat(findPyProjectTomlFilesInIndex(setOf(root), emptySet()))
      .describedAs("the index knows nothing below the root before the load")
      .isEmpty()

    loadSubtreesIntoVfs(setOf(rootFile))

    assertThat(findPyProjectTomlFilesInIndex(setOf(root), emptySet()))
      .describedAs("the load makes a nested file visible")
      .contains(nested)
  }
}
