// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pyproject

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.writeText
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.autoImportBridge.PyExternalSystemProjectAware
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.testFramework.utils.vfs.createFile
import com.jetbrains.python.PyNames
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

/**
 * Verifies that pyproject.toml model rebuild propagates the Python entity source to all children
 * of content roots (source roots, exclude URLs), so that `replaceBySource` handles them correctly.
 *
 * PY-87499: `replaceBySource` skips child entities whose entity source doesn't match the filter.
 * If excluded URLs keep JPS entity source while their parent content root gets Python entity source,
 * they are silently dropped during replacement on subsequent project reloads.
 */
@Timeout(2, unit = TimeUnit.MINUTES)
@TestApplication
internal class PyExcludeUrlPreservationTest {
  private val tempDirFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture = tempDirFixture)

  /**
   * The core regression test: after a JPS module is synced with pyproject.toml,
   * all workspace entities (content roots, source roots, exclude URLs) must have
   * a consistent Python entity source. On the unfixed code, children keep JPS source
   * which causes `replaceBySource` to drop them on subsequent reloads.
   */
  @Test
  fun `exclude URL entity source matches parent after rebuild`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val root = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(tempDirFixture.get())!!

    // Create a JPS module — simulates a module loaded from .iml on project open
    val module = edtWriteAction {
      ModuleManager.getInstance(project).newNonPersistentModule("myproject", PyNames.PYTHON_MODULE_ID)
    }

    // Set up content root with an excluded folder
    writeAction {
      val model = ModuleRootManager.getInstance(module).modifiableModel
      val entry = model.addContentEntry(root)
      entry.addExcludeFolder(root.createDirectory("node_modules"))
      model.commit()
    }

    // Create pyproject.toml so the sync processes this module
    writeAction {
      root.createFile(PY_PROJECT_TOML).writeText("""
        [project]
        name = "myproject"
      """.trimIndent())
    }

    // Trigger pyproject sync — this is where the entity source mismatch occurred
    val sut = PyExternalSystemProjectAware.create(project)
    sut.reloadProjectImpl()

    // Verify: all entities under pyproject-managed content roots must have Python entity source.
    // On the unfixed code, ExcludeUrlEntity keeps JPS source → this assertion fails.
    val snapshot = project.workspaceModel.currentSnapshot
    for (contentRoot in snapshot.entities<ContentRootEntity>()) {
      val contentSource = contentRoot.entitySource
      if (contentSource !is JpsImportedEntitySource) continue
      if (contentSource.externalSystemId != "pyproject.toml") continue

      for (excludeUrl in contentRoot.excludedUrls) {
        val excludeSource = excludeUrl.entitySource
        assertTrue(
          excludeSource is JpsImportedEntitySource && excludeSource.externalSystemId == "pyproject.toml",
          "ExcludeUrlEntity '${excludeUrl.url}' has entity source $excludeSource, " +
          "expected pyproject.toml entity source matching its parent ContentRootEntity"
        )
      }

      for (sourceRoot in contentRoot.sourceRoots) {
        val srSource = sourceRoot.entitySource
        assertTrue(
          srSource is JpsImportedEntitySource && srSource.externalSystemId == "pyproject.toml",
          "SourceRootEntity '${sourceRoot.url}' has entity source $srSource, " +
          "expected pyproject.toml entity source matching its parent ContentRootEntity"
        )
      }
    }

    // Also verify the exclude itself is present
    val currentModule = project.modules.single()
    val excludeNames = currentModule.rootManager.excludeRoots.map { it.name }
    assertTrue("node_modules" in excludeNames, "Excluded folder should be present after rebuild, found: $excludeNames")
  }

  /**
   * Verifies source root entity source consistency after rebuild.
   */
  @Test
  fun `source root entity source matches parent after rebuild`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val root = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(tempDirFixture.get())!!

    val module = edtWriteAction {
      ModuleManager.getInstance(project).newNonPersistentModule("myproject", PyNames.PYTHON_MODULE_ID)
    }

    writeAction {
      val model = ModuleRootManager.getInstance(module).modifiableModel
      val entry = model.addContentEntry(root)
      entry.addSourceFolder(root.createDirectory("lib"), false)
      model.commit()
    }

    writeAction {
      root.createFile(PY_PROJECT_TOML).writeText("""
        [project]
        name = "myproject"
      """.trimIndent())
    }

    val sut = PyExternalSystemProjectAware.create(project)
    sut.reloadProjectImpl()

    val snapshot = project.workspaceModel.currentSnapshot
    val pyContentRoots = snapshot.entities<ContentRootEntity>().filter {
      val src = it.entitySource
      src is JpsImportedEntitySource && src.externalSystemId == "pyproject.toml"
    }.toList()

    assertTrue(pyContentRoots.isNotEmpty(), "Should have pyproject-managed content roots")

    for (contentRoot in pyContentRoots) {
      for (sourceRoot in contentRoot.sourceRoots) {
        val srSource = sourceRoot.entitySource
        assertTrue(
          srSource is JpsImportedEntitySource && srSource.externalSystemId == "pyproject.toml",
          "SourceRootEntity '${sourceRoot.url}' has entity source $srSource, " +
          "expected pyproject.toml entity source matching its parent ContentRootEntity"
        )
      }
    }

    // Verify 'lib' source root is present
    val currentModule = project.modules.single()
    val sourceNames = currentModule.rootManager.sourceRoots.map { it.name }
    assertTrue("lib" in sourceNames, "Source root 'lib' should be present after rebuild, found: $sourceNames")
  }

  /**
   * Verifies that entity sources are consistent in a multi-module workspace.
   */
  @Test
  fun `entity sources are consistent in multi-module project`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val root = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(tempDirFixture.get())!!

    // Create a JPS module with content root at project root
    val module = edtWriteAction {
      ModuleManager.getInstance(project).newNonPersistentModule("root-project", PyNames.PYTHON_MODULE_ID)
    }
    val secondDir = writeAction { root.createDirectory("second") }
    writeAction {
      val model = ModuleRootManager.getInstance(module).modifiableModel
      val entry = model.addContentEntry(root)
      entry.addExcludeFolder(root.createDirectory("dist"))
      model.commit()
    }

    // Create pyproject.toml files for root and a workspace member
    writeAction {
      root.createFile(PY_PROJECT_TOML).writeText("""
        [project]
        name = "root-project"
      """.trimIndent())
      secondDir.createFile(PY_PROJECT_TOML).writeText("""
        [project]
        name = "second"
      """.trimIndent())
    }

    val sut = PyExternalSystemProjectAware.create(project)
    sut.reloadProjectImpl()

    // Verify all pyproject modules have consistent entity sources on all children
    val snapshot = project.workspaceModel.currentSnapshot
    for (moduleEntity in snapshot.entities<ModuleEntity>()) {
      val moduleSource = moduleEntity.entitySource
      if (moduleSource !is JpsImportedEntitySource || moduleSource.externalSystemId != "pyproject.toml") continue

      for (contentRoot in moduleEntity.contentRoots) {
        for (excludeUrl in contentRoot.excludedUrls) {
          val src = excludeUrl.entitySource
          assertEquals(
            "pyproject.toml",
            (src as? JpsImportedEntitySource)?.externalSystemId,
            "ExcludeUrlEntity in module '${moduleEntity.name}' has wrong entity source: $src"
          )
        }
      }
    }
  }
}
