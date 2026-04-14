// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pyproject.model

import com.intellij.openapi.application.writeAction
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleTypeId
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.jps.entities.modifyContentRootEntity
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.workspaceModel.ide.legacyBridge.LegacyBridgeJpsEntitySourceFactory
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for source/exclude root relocation when pyproject.toml sub-modules are discovered
 * inside an existing parent module's content root (PY-89073).
 */
@TestApplication
internal class PyProjectTomlRelocationTest {

  private val tempDirFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  /**
   * PY-89073: a non-pyproject Python module at the root with source and exclude roots inside
   * sub-directories that become pyproject modules. After sync, each sub-module should own the
   * source/exclude roots that belong to its content root, and the parent module should no longer
   * have them.
   */
  @Test
  fun `source and exclude roots reassigned from parent Python module to inner pyproject modules`(): Unit = timeoutRunBlocking(30.seconds) {
    val libADir = writeAction {
      val a = f.root.createDirectory("lib-a")
      a.createDirectory("src")
      a.createDirectory(".venv")
      a.writePyprojectToml("lib-a")
      a
    }
    val libBDir = writeAction {
      val b = f.root.createDirectory("lib-b")
      b.createDirectory("tests")
      b.createDirectory(".cache")
      b.writePyprojectToml("lib-b")
      b
    }

    // Create a non-pyproject Python module "root-py" spanning the entire root,
    // with source roots and excludes inside both sub-directories.
    val virtualFileUrlManager = f.project.workspaceModel.getVirtualFileUrlManager()
    val rootUrl = f.root.toNioPath().toVirtualFileUrl(virtualFileUrlManager)
    val srcUrl = libADir.findChild("src")!!.toNioPath().toVirtualFileUrl(virtualFileUrlManager)
    val venvUrl = libADir.findChild(".venv")!!.toNioPath().toVirtualFileUrl(virtualFileUrlManager)
    val testsUrl = libBDir.findChild("tests")!!.toNioPath().toVirtualFileUrl(virtualFileUrlManager)
    val cacheUrl = libBDir.findChild(".cache")!!.toNioPath().toVirtualFileUrl(virtualFileUrlManager)
    val entitySource = LegacyBridgeJpsEntitySourceFactory.getInstance(f.project)
      .createEntitySourceForModule(rootUrl, null)
    f.project.workspaceModel.update("add root Python module with source/exclude roots") { storage ->
      storage addEntity ModuleEntity("root-py", emptyList(), entitySource) {
        type = ModuleTypeId(PYTHON)
        contentRoots = listOf(ContentRootEntity(rootUrl, emptyList(), entitySource) {
          sourceRoots = listOf(
            SourceRootEntity(srcUrl, SourceRootTypeId("java-source"), entitySource),
            SourceRootEntity(testsUrl, SourceRootTypeId("java-test"), entitySource),
          )
          excludedUrls = listOf(
            ExcludeUrlEntity(venvUrl, entitySource),
            ExcludeUrlEntity(cacheUrl, entitySource),
          )
        })
      }
    }

    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("lib-a", contentRoot = "lib-a", sourceRoots = listOf("lib-a/src"), excludedFolders = listOf("lib-a/.venv")),
      ExpectedModule("lib-b", contentRoot = "lib-b", sourceRoots = listOf("lib-b/tests"), excludedFolders = listOf("lib-b/.cache")),
      ExpectedModule("root-py", type = PYTHON, contentRoot = "."),
    )
  }

  /**
   * PY-89073: when migrating from single-module to multi-module, source/exclude roots that belong
   * to a sub-project's directory should be relocated to the child module, not dropped.
   */
  @Test
  fun `source roots in sub-project are relocated to child module`(): Unit = timeoutRunBlocking(30.seconds) {
    writeAction {
      f.root.writePyprojectToml("parent")
      val sub = f.root.createDirectory("sub")
      sub.writePyprojectToml("child")
      sub.createDirectory("src")
    }

    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("child", contentRoot = "sub", sourceRoots = listOf("sub/src")),
      ExpectedModule("parent", contentRoot = "."),
    )
  }

  /**
   * PY-89073: template and resource roots marked on a parent module inside a sub-project's directory
   * should be relocated to the child module, not dropped.
   */
  @Test
  fun `template and resource roots in sub-project are relocated to child module`(): Unit = timeoutRunBlocking(30.seconds) {
    writeAction {
      f.root.writePyprojectToml("parent")
      val sub = f.root.createDirectory("sub")
      sub.writePyprojectToml("child")
      sub.createDirectory("templates")
      sub.createDirectory("resources")
    }

    // First sync creates the modules
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("child", contentRoot = "sub"),
      ExpectedModule("parent", contentRoot = "."),
    )

    // Add template and resource roots on the parent module inside the child's directory
    val virtualFileUrlManager = f.project.workspaceModel.getVirtualFileUrlManager()
    val templatesUrl = f.root.findChild("sub")!!.findChild("templates")!!.toNioPath().toVirtualFileUrl(virtualFileUrlManager)
    val resourcesUrl = f.root.findChild("sub")!!.findChild("resources")!!.toNioPath().toVirtualFileUrl(virtualFileUrlManager)
    f.project.workspaceModel.update("add template and resource roots") { storage ->
      val parentModule = storage.resolve(ModuleId("parent"))!!
      val contentRoot = parentModule.contentRoots.firstOrNull()
      if (contentRoot != null) {
        storage.modifyContentRootEntity(contentRoot) {
          sourceRoots = sourceRoots + listOf(
            SourceRootEntity(templatesUrl, SourceRootTypeId("python-template"), this.entitySource),
            SourceRootEntity(resourcesUrl, SourceRootTypeId("java-resource"), this.entitySource),
          )
        }
      }
    }

    // Second sync should relocate the roots to the child module
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("child", contentRoot = "sub", sourceRoots = listOf("sub/templates", "sub/resources")),
      ExpectedModule("parent", contentRoot = "."),
    )
  }

  /**
   * PY-89073: excluded folders inside a sub-project's directory should be relocated to the child module.
   */
  @Test
  fun `excluded folders in sub-project are relocated to child module`(): Unit = timeoutRunBlocking(30.seconds) {
    writeAction {
      f.root.writePyprojectToml("parent")
      val sub = f.root.createDirectory("sub")
      sub.writePyprojectToml("child")
      sub.createDirectory(".venv")
    }

    // First sync creates the modules
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("child", contentRoot = "sub"),
      ExpectedModule("parent", contentRoot = "."),
    )

    // Mark .venv as excluded on the parent module inside the child's directory
    val virtualFileUrlManager = f.project.workspaceModel.getVirtualFileUrlManager()
    val venvUrl = f.root.findChild("sub")!!.findChild(".venv")!!.toNioPath().toVirtualFileUrl(virtualFileUrlManager)
    f.project.workspaceModel.update("add exclude") { storage ->
      val parentModule = storage.resolve(ModuleId("parent"))!!
      val contentRoot = parentModule.contentRoots.firstOrNull()
      if (contentRoot != null) {
        storage.modifyContentRootEntity(contentRoot) {
          excludedUrls = excludedUrls + ExcludeUrlEntity(venvUrl, this.entitySource)
        }
      }
    }

    // Second sync should relocate the exclude to the child module
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule("child", contentRoot = "sub", excludedFolders = listOf("sub/.venv")),
      ExpectedModule("parent", contentRoot = "."),
    )
  }
}
