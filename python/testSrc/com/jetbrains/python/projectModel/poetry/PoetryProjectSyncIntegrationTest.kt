// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.intellij.openapi.externalSystem.testFramework.fixtures.multiProjectFixture
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.moduleAssertion.ContentRootAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions.INHERITED_SDK
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions.MODULE_SOURCE
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.SourceRootAssertions
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.io.createFile
import com.jetbrains.python.projectModel.BaseProjectModelService.Companion.PYTHON_SOURCE_ROOT_TYPE
import com.jetbrains.python.projectModel.uv.UvConstants
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.io.path.writeText

@RegistryKey("python.project.model.poetry", "true")
@TestApplication
class PoetryProjectSyncIntegrationTest {
  private val testRootFixture = tempPathFixture()
  val testRoot by testRootFixture

  private val project by projectFixture(testRootFixture, openAfterCreation = true)
  private val multiprojectFixture by multiProjectFixture()

  @Test
  fun `src directory is mapped to module source root`() = timeoutRunBlocking {
    testRoot.createFile("pyproject.toml").writeText("""
      [project]
      name = "main"
      dependencies = [
      ]
      
      [tool.poetry]
      packages = [{include = "main", from = "src"}]
    """.trimIndent())

    testRoot.createFile("src/main/__init__.py")

    multiprojectFixture.linkProject(project, testRoot, UvConstants.SYSTEM_ID)
    syncAllProjects(project)

    ModuleAssertions.assertModules(project, "main")
    SourceRootAssertions.assertSourceRoots(project, "main", { it.rootTypeId == PYTHON_SOURCE_ROOT_TYPE }, testRoot.resolve("src"))
  }

  @Test
  fun `project with new-style PEP-621 path dependencies`() = timeoutRunBlocking {
    testRoot.createFile("pyproject.toml").writeText("""
      [project]
      name = "main"
      dependencies = [
          "lib @ ${testRoot.toUri()}/lib"
      ]
    """.trimIndent())

    testRoot.createFile("lib/pyproject.toml").writeText("""
      [project]
      name = "lib"
      dependencies = [
      ]
    """.trimIndent())

    multiprojectFixture.linkProject(project, testRoot, PoetryConstants.SYSTEM_ID)
    syncAllProjects(project)

    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    ModuleAssertions.assertModules(project, "main", "lib")
    ModuleAssertions.assertModuleEntity(project, "main") { module ->
      ContentRootAssertions.assertContentRoots(virtualFileUrlManager, module, testRoot)
      DependencyAssertions.assertDependencies(module, INHERITED_SDK, MODULE_SOURCE, "lib")
      DependencyAssertions.assertModuleDependency(module, "lib") { dependency ->
        Assertions.assertTrue(dependency.exported)
      }
    }

    ModuleAssertions.assertModuleEntity(project, "lib") { module ->
      ContentRootAssertions.assertContentRoots(virtualFileUrlManager, module, testRoot.resolve("lib"))
      DependencyAssertions.assertDependencies(module, INHERITED_SDK, MODULE_SOURCE)
    }
  }

  // This format of path dependencies was used before Poetry 2.0
  // https://python-poetry.org/history/#added-2
  @Test
  fun `project with old-style path dependencies`() = timeoutRunBlocking {
    testRoot.createFile("pyproject.toml").writeText("""
      [tool.poetry]
      name = "main"
      
      [tool.poetry.dependencies]
      lib = {path = "./lib", develop = true}
    """.trimIndent())

    testRoot.createFile("lib/pyproject.toml").writeText("""
      [tool.poetry]
      name = "lib"
    """.trimIndent())

    multiprojectFixture.linkProject(project, testRoot, PoetryConstants.SYSTEM_ID)
    syncAllProjects(project)

    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    ModuleAssertions.assertModules(project, "main", "lib")
    ModuleAssertions.assertModuleEntity(project, "main") { module ->
      ContentRootAssertions.assertContentRoots(virtualFileUrlManager, module, testRoot)
      DependencyAssertions.assertDependencies(module, INHERITED_SDK, MODULE_SOURCE, "lib")
      DependencyAssertions.assertModuleDependency(module, "lib") { dependency ->
        Assertions.assertTrue(dependency.exported)
      }
    }

    ModuleAssertions.assertModuleEntity(project, "lib") { module ->
      ContentRootAssertions.assertContentRoots(virtualFileUrlManager, module, testRoot.resolve("lib"))
      DependencyAssertions.assertDependencies(module, INHERITED_SDK, MODULE_SOURCE)
    }
  }
  
  suspend fun syncAllProjects(project: Project) {
    multiprojectFixture.awaitProjectConfiguration(project) {
      PoetryProjectModelService.syncAllProjectModelRoots(project)
    }
  }
}