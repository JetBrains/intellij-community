// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.uv

import com.intellij.openapi.externalSystem.testFramework.fixtures.multiProjectFixture
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.moduleAssertion.ContentRootAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.io.createFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.io.path.writeText

@RegistryKey("python.project.model.uv", "true")
@TestApplication
class PyUvSyncIntegrationTest {
  private val testRootFixture = tempPathFixture()
  val testRoot by testRootFixture

  private val project by projectFixture(testRootFixture, openAfterCreation = true)
  private val multiprojectFixture by multiProjectFixture()

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
    ModuleAssertions.assertModuleEntity(project, "main") { module ->
      ContentRootAssertions.assertContentRoots(virtualFileUrlManager, module, testRoot)
      DependencyAssertions.assertDependencies(module, DependencyAssertions.INHERITED_SDK, DependencyAssertions.MODULE_SOURCE, "lib1", "lib2")
      DependencyAssertions.assertModuleDependency(module, "lib1") { dependency ->
        Assertions.assertTrue(dependency.exported)
      }
      DependencyAssertions.assertModuleDependency(module, "lib2") { dependency ->
        Assertions.assertTrue(dependency.exported)
      }
    }

    ModuleAssertions.assertModuleEntity(project, "lib1") { module ->
      ContentRootAssertions.assertContentRoots(virtualFileUrlManager, module, testRoot.resolve("lib/lib1"))
      DependencyAssertions.assertDependencies(module, DependencyAssertions.INHERITED_SDK, DependencyAssertions.MODULE_SOURCE, "pkg")
      DependencyAssertions.assertModuleDependency(module, "pkg") { dependency ->
        Assertions.assertTrue(dependency.exported)
      }
    }

    ModuleAssertions.assertModuleEntity(project, "lib2") { module ->
      ContentRootAssertions.assertContentRoots(virtualFileUrlManager, module, testRoot.resolve("lib/lib2"))
      DependencyAssertions.assertDependencies(module, DependencyAssertions.INHERITED_SDK, DependencyAssertions.MODULE_SOURCE)
    }

    ModuleAssertions.assertModuleEntity(project, "pkg") { module ->
      ContentRootAssertions.assertContentRoots(virtualFileUrlManager, module, testRoot.resolve("packages/pkg"))
      DependencyAssertions.assertDependencies(module, DependencyAssertions.INHERITED_SDK, DependencyAssertions.MODULE_SOURCE)
    }
  }
  
  suspend fun syncAllProjects(project: Project) {
    multiprojectFixture.awaitProjectConfiguration(project) {
      UvProjectModelService.syncAllProjectModelRoots(project)
    }
  }
}