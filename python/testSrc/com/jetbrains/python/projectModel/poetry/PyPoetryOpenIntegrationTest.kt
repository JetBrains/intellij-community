// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.testFramework.fixtures.multiProjectFixture
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.utils.io.createFile
import org.junit.jupiter.api.Test
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@RegistryKey("python.project.model.poetry", "true")
@TestApplication
class PyPoetryOpenIntegrationTest {
  private val testRoot by tempPathFixture()
  private val multiprojectFixture by multiProjectFixture()

  @Test
  fun `project with top-level legacy pyproject-toml is automatically linked`() = timeoutRunBlocking(timeout = 20.seconds) {
    val projectPath = testRoot.resolve("project")

    projectPath.createFile("pyproject.toml").writeText("""
      [tool.poetry]
      name = "project"
    """.trimIndent())

    multiprojectFixture.openProject(projectPath).useProjectAsync { project ->
      ModuleAssertions.assertModules(project, "project")
      CollectionAssertions.assertEqualsUnordered(listOf(projectPath),
                                                 project.service<PoetrySettings>().getLinkedProjects())
    }
  }

  @Test
  fun `project with top-level PEP-621 pyproject-toml without tool-poetry table is not automatically linked`() = timeoutRunBlocking(timeout = 20.seconds) {
    val projectPath = testRoot.resolve("project")
    projectPath.createFile("pyproject.toml").writeText("""
      [project]
      name = "project"
    """.trimIndent())

    multiprojectFixture.openProject(projectPath).useProjectAsync { project ->
      ModuleAssertions.assertModules(project, "project")
      CollectionAssertions.assertEqualsUnordered(emptyList(), project.service<PoetrySettings>().getLinkedProjects())
    }
  }


  @Test
  fun `project with top-level PEP-621 pyproject-toml containing tool-poetry table is automatically linked`() = timeoutRunBlocking(timeout = 20.seconds) {
    val projectPath = testRoot.resolve("project")
    
    projectPath.createFile("pyproject.toml").writeText("""
      [project]
      name = "project"
      
      [tool.poetry]
      requires-poetry = ">=2.0"
    """.trimIndent())

    multiprojectFixture.openProject(projectPath).useProjectAsync { project ->
      ModuleAssertions.assertModules(project, "project")
      CollectionAssertions.assertEqualsUnordered(listOf(projectPath),
                                                 project.service<PoetrySettings>().getLinkedProjects())
    }
  }

  @Test
  fun `project with top-level poetry-lock is automatically linked`() = timeoutRunBlocking(timeout = 20.seconds) {
    val projectPath = testRoot.resolve("project")
    
    projectPath.createFile("poetry.lock").writeText("""""")

    projectPath.createFile("pyproject.toml").writeText("""
      [project]
      name = "project"
    """.trimIndent())

    multiprojectFixture.openProject(projectPath).useProjectAsync { project ->
      ModuleAssertions.assertModules(project, "project")
      CollectionAssertions.assertEqualsUnordered(listOf(projectPath),
                                                 project.service<PoetrySettings>().getLinkedProjects())
    }
  }

  @Test
  fun `test monorepo without top-level pyproject-toml and with sibling path dependency`() = timeoutRunBlocking(timeout = 20.seconds) {
    val projectPath = testRoot.resolve("project")
    projectPath.createFile("libs/project1/pyproject.toml").writeText("""
      [tool.poetry]
      name = "project1"
      
      [tool.poetry.dependencies]
      project2 = { path = "../project2", develop = true }
    """.trimIndent())

    projectPath.createFile("libs/project2/pyproject.toml").writeText("""
      [tool.poetry]
      name = "project2"
    """.trimIndent())

    multiprojectFixture.openProject(projectPath).useProjectAsync { project ->
      val poetrySettings = project.service<PoetrySettings>()
      // Such projects without pyproject.toml or poetry.lock in the root cannot be recognized automatically
      CollectionAssertions.assertEmpty(poetrySettings.getLinkedProjects())
      ModuleAssertions.assertModules(project, "project")

      multiprojectFixture.awaitProjectConfiguration(project) {
        PoetryProjectModelService.linkAllProjectModelRoots(project, project.basePath!!)
        PoetryProjectModelService.syncAllProjectModelRoots(project)
      }

      ModuleAssertions.assertModules(project, "project", "project1", "project2")
      CollectionAssertions.assertEqualsUnordered(
        listOf(projectPath.resolve("libs")),
        poetrySettings.getLinkedProjects()
      )

      ModuleAssertions.assertModuleEntity(project, "project1") { module ->
        DependencyAssertions.assertDependencies(module, DependencyAssertions.INHERITED_SDK, DependencyAssertions.MODULE_SOURCE, "project2")
      }

      ModuleAssertions.assertModuleEntity(project, "project2") { module ->
        DependencyAssertions.assertDependencies(module, DependencyAssertions.INHERITED_SDK, DependencyAssertions.MODULE_SOURCE)
      }
    }
  }
}