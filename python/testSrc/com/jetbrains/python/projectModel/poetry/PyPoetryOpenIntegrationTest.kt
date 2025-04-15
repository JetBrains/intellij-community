// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.testFramework.fixtures.multiProjectFixture
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
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
  fun `project without dot-idea with pyproject-toml is automatically linked`() = timeoutRunBlocking(timeout = 20.seconds) {
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
}