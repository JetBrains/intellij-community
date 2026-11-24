// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve

import com.intellij.idea.TestFor
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText

/**
 * A creation, a deletion, a move, a copy and a rename drop the path cache. A content change does not.
 * A VFS refresh can bring thousands of file events, for example when a tool deletes or creates a virtual environment.
 * The cache checks the VFS structure count when it is read, so no event walks the cache.
 */
@TestFor(classes = [PythonPathCache::class, PythonSdkPathCache::class], issues = ["PY-93901"])
@Subsystems.CodeInsight
@Layers.Functional
class PythonPathCacheVfsInvalidationTest : PyCodeInsightTestCase() {

  @TempDir
  lateinit var tempDir: Path

  private val cachedName = QualifiedName.fromComponents("cached")

  private val moduleCache: PythonPathCache
    get() = runReadActionBlocking { PythonPathCache.getInstance(myFixture.module) }

  @Test
  fun `a refresh that finds many new files drops the path cache`() {
    val dir = tempDir.resolve("many")
    val vDir = loadDirectory(dir)
    writeModules(dir, 50)
    assertClearedBy { refresh(vDir) }
  }

  @Test
  fun `a refresh that finds many deleted files drops the path cache`() {
    val dir = tempDir.resolve("many")
    val vDir = loadDirectory(dir)
    val files = writeModules(dir, 50)
    refresh(vDir)
    files.forEach { it.deleteExisting() }
    assertClearedBy { refresh(vDir) }
  }

  @Test
  fun `a rename clears the path cache`() {
    val file = createModule("renamed.py")
    assertClearedBy { file.rename(this, "renamed_new.py") }
  }

  @Test
  fun `a move clears the path cache`() {
    val file = createModule("moved.py")
    val target = loadDirectory(tempDir.resolve("target"))
    assertClearedBy { file.move(this, target) }
  }

  @Test
  fun `a copy clears the path cache`() {
    val file = createModule("copied.py")
    assertClearedBy { file.copy(this, file.parent, "copied_new.py") }
  }

  @Test
  fun `a content change keeps the path cache`() {
    val file = createModule("changed.py")
    fillModuleCache()
    WriteAction.runAndWait<Throwable> { VfsUtil.saveText(file, "y = 2") }
    assertNotNull(moduleCache.get(cachedName))
  }

  private fun assertClearedBy(action: () -> Unit) {
    fillModuleCache()
    WriteAction.runAndWait<Throwable> { action() }
    assertNull(moduleCache.get(cachedName))
  }

  private fun createModule(name: String): VirtualFile {
    val dir = tempDir.resolve("source")
    val vDir = loadDirectory(dir)
    dir.resolve(name).writeText("x = 1")
    refresh(vDir)
    return vDir.findChild(name)!!
  }

  private fun fillModuleCache() {
    moduleCache.put(cachedName, emptyList())
    assertNotNull(moduleCache.get(cachedName))
  }

  private fun loadDirectory(dir: Path): VirtualFile {
    dir.createDirectories()
    val vDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)!!
    vDir.children
    return vDir
  }

  private fun writeModules(dir: Path, fileCount: Int): List<Path> =
    (1..fileCount).map { index -> dir.resolve("module$index.py").also { it.writeText("x = $index") } }

  private fun refresh(vDir: VirtualFile) {
    VfsUtil.markDirtyAndRefresh(false, true, true, vDir)
    assertEquals(Files.list(vDir.toNioPath()).use { it.count() }.toInt(), vDir.children.size)
  }
}
