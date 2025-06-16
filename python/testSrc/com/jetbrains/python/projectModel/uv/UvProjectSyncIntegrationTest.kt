// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.uv

import com.intellij.openapi.externalSystem.testFramework.fixtures.multiProjectFixture
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.platform.testFramework.assertion.moduleAssertion.ContentRootAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.SourceRootAssertions
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.io.createFile
import com.jetbrains.python.projectModel.BaseProjectModelService.Companion.PYTHON_SOURCE_ROOT_TYPE
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.writeText

@RegistryKey("python.project.model.uv", "true")
@TestApplication
class UvProjectSyncIntegrationTest {
  private val testRootFixture = tempPathFixture()
  val testRoot by testRootFixture

  private val project by projectFixture(testRootFixture, openAfterCreation = true)
  private val multiprojectFixture by multiProjectFixture()

  @Test
  fun `src directory is mapped to module source root`() = timeoutRunBlocking {
    testRoot.createFile("pyproject.toml").writeText("""
      [project]
      name = "main"
      dependencies = []
      
      [build-system]
      requires = ["hatchling"]
      build-backend = "hatchling.build"
    """.trimIndent())

    testRoot.createFile("src/main/__init__.py")

    multiprojectFixture.linkProject(project, testRoot, UvConstants.SYSTEM_ID)
    syncAllProjects(project)

    ModuleAssertions.assertModules(project, "main")
    SourceRootAssertions.assertSourceRoots(project, "main", { it.rootTypeId == PYTHON_SOURCE_ROOT_TYPE }, testRoot.resolve("src"))
  }

  @Test
  fun `root dot venv directory is automatically excluded`() = timeoutRunBlocking {
    testRoot.createFile("pyproject.toml").writeText("""
      [project]
      name = "main"
      dependencies = []
      
      [tool.uv.workspace]
      members = [
          "lib",
      ]
    """.trimIndent())
    testRoot.createFile(".venv/pyvenv.cfg")

    testRoot.createFile("lib/pyproject.toml").writeText("""
      [project]
      name = "lib"
      dependencies = []
    """.trimIndent())
    testRoot.createFile("lib/.venv/pyvenv.cfg")

    multiprojectFixture.linkProject(project, testRoot, UvConstants.SYSTEM_ID)
    syncAllProjects(project)

    ModuleAssertions.assertModules(project, "main", "lib")
    assertExcludedRoots(project, "main", listOf(testRoot.resolve(".venv")))
    assertExcludedRoots(project, "lib", listOf(testRoot.resolve("lib/.venv")))
  }

  @Test
  fun `projects inside dot venv are skipped`() = timeoutRunBlocking {
    testRoot.createFile("pyproject.toml").writeText("""
      [project]
      name = "main"
    """.trimIndent())

    testRoot.createFile(".venv/lib/python3.13/site-packages/pandas/pyproject.toml").writeText("""
      [project]
      name = 'pandas'
    """.trimIndent())

    multiprojectFixture.linkProject(project, testRoot, UvConstants.SYSTEM_ID)
    syncAllProjects(project)

    ModuleAssertions.assertModules(project, "main")
  }

  @Test
  fun `workspace project with a path dependency`() = timeoutRunBlocking {
    testRoot.createFile("pyproject.toml").writeText("""
      [project]
      name = "main"
      dependencies = [
          "lib1",
          "lib2",
      ]
      
      [tool.uv.workspace]
      members = ["lib/lib1", "lib/lib2"]
      
      [tool.uv.sources]
      lib1 = { workspace = true }
      lib2 = { workspace = true }
    """.trimIndent())

    testRoot.createFile("lib/lib1/pyproject.toml").writeText("""
      [project]
      name = "lib1"
      dependencies = [
          "pkg",
      ]
      
      [tool.uv.sources]
      pkg = { path = "../../packages/pkg", editable = true }
    """.trimIndent())

    testRoot.createFile("lib/lib2/pyproject.toml").writeText("""
      [project]
      name = "lib2"
      dependencies = []
    """.trimIndent())

    testRoot.createFile("packages/pkg/pyproject.toml").writeText("""
      [project]
      name = "pkg"
      dependencies = []
    """.trimIndent())

    multiprojectFixture.linkProject(project, testRoot, UvConstants.SYSTEM_ID)
    syncAllProjects(project)

    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    ModuleAssertions.assertModules(project, "main", "lib1", "lib2", "pkg")
    val workspace = UvProjectModelService.UvWorkspace<ModuleEntity>(
      root = project.findModule("main")!!,
      members = setOf(
        project.findModule("lib1")!!,
        project.findModule("lib2")!!,
      )
    )

    ModuleAssertions.assertModuleEntity(project, "main") { module ->
      ContentRootAssertions.assertContentRoots(virtualFileUrlManager, module, testRoot)
      DependencyAssertions.assertDependencies(module, DependencyAssertions.INHERITED_SDK, DependencyAssertions.MODULE_SOURCE, "lib1", "lib2")
      DependencyAssertions.assertModuleDependency(module, "lib1") { dependency ->
        Assertions.assertTrue(dependency.exported)
      }
      DependencyAssertions.assertModuleDependency(module, "lib2") { dependency ->
        Assertions.assertTrue(dependency.exported)
      }
      Assertions.assertEquals("main", module.exModuleOptions?.linkedProjectId)
      Assertions.assertEquals(workspace, UvProjectModelService.findWorkspace(project, module)
      )
    }

    ModuleAssertions.assertModuleEntity(project, "lib1") { module ->
      ContentRootAssertions.assertContentRoots(virtualFileUrlManager, module, testRoot.resolve("lib/lib1"))
      DependencyAssertions.assertDependencies(module, DependencyAssertions.INHERITED_SDK, DependencyAssertions.MODULE_SOURCE, "pkg")
      DependencyAssertions.assertModuleDependency(module, "pkg") { dependency ->
        Assertions.assertTrue(dependency.exported)
      }
      Assertions.assertEquals("main:lib1", module.exModuleOptions?.linkedProjectId)
      Assertions.assertEquals(workspace, UvProjectModelService.findWorkspace(project, module)
      )
    }

    ModuleAssertions.assertModuleEntity(project, "lib2") { module ->
      ContentRootAssertions.assertContentRoots(virtualFileUrlManager, module, testRoot.resolve("lib/lib2"))
      DependencyAssertions.assertDependencies(module, DependencyAssertions.INHERITED_SDK, DependencyAssertions.MODULE_SOURCE)
      Assertions.assertEquals("main:lib2", module.exModuleOptions?.linkedProjectId)
      Assertions.assertEquals(workspace, UvProjectModelService.findWorkspace(project, module)
      )
    }

    ModuleAssertions.assertModuleEntity(project, "pkg") { module ->
      ContentRootAssertions.assertContentRoots(virtualFileUrlManager, module, testRoot.resolve("packages/pkg"))
      DependencyAssertions.assertDependencies(module, DependencyAssertions.INHERITED_SDK, DependencyAssertions.MODULE_SOURCE)
      Assertions.assertEquals("pkg", module.exModuleOptions?.linkedProjectId)
      Assertions.assertEquals(
        UvProjectModelService.UvWorkspace(module, emptySet()),
        UvProjectModelService.findWorkspace(project, module)
      )
    }
  }

  private fun assertExcludedRoots(project: Project, moduleName: String, expectedRoots: List<Path>) {
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    val expectedUrls = expectedRoots.map { it.normalize().toVirtualFileUrl(virtualFileUrlManager) }
    ModuleAssertions.assertModuleEntity(project, moduleName) { moduleEntity ->
      val actualRoots = moduleEntity.contentRoots
        .flatMap { it.excludedUrls }
        .map { it.url }
      assertEqualsUnordered(expectedUrls, actualRoots)
    }
  }

  private fun Project.findModule(name: String): ModuleEntity? {
    return workspaceModel.currentSnapshot.resolve(ModuleId(name))
  }

  private suspend fun syncAllProjects(project: Project) {
    multiprojectFixture.awaitProjectConfiguration(project) {
      UvProjectModelService.syncAllProjectModelRoots(project)
    }
  }
}