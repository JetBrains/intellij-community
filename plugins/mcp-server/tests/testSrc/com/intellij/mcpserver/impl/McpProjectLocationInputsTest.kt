package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.impl.util.projectPathParameterName
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class McpProjectLocationInputsTest {
  companion object {
    val firstProjectFixture = projectFixture(openAfterCreation = true)
    val firstProject by firstProjectFixture
    val secondProjectFixture = projectFixture(openAfterCreation = true)
    val secondProject by secondProjectFixture
  }

  @Test
  fun `projectPath argument wins over all other sources`() {
    runBlocking(Dispatchers.Default) {
      val project = McpProjectLocationInputs(
        projectPathFromArgument = secondProject.basePath,
        projectPathFromCallHeader = firstProject.basePath,
        projectPathFromSessionHeader = firstProject.basePath,
        roots = setOf(projectRootUri(firstProject)),
      ).resolveProject()

      assertThat(project).isEqualTo(secondProject)
    }
  }

  @Test
  fun `invalid projectPath argument throws without fallback`() {
    assertThatThrownBy {
      runBlocking(Dispatchers.Default) {
        McpProjectLocationInputs(
          projectPathFromArgument = "/tmp/not-an-open-project",
          projectPathFromCallHeader = firstProject.basePath,
          projectPathFromSessionHeader = secondProject.basePath,
          roots = setOf(projectRootUri(secondProject)),
        ).resolveProject()
      }
    }
      .isInstanceOf(McpExpectedError::class.java)
      .hasMessageContaining("`${projectPathParameterName}`=`/tmp/not-an-open-project` doesn't correspond to any open project.")
  }

  @Test
  fun `call header wins over session header`() {
    runBlocking(Dispatchers.Default) {
      val project = McpProjectLocationInputs(
        projectPathFromArgument = null,
        projectPathFromCallHeader = secondProject.basePath,
        projectPathFromSessionHeader = firstProject.basePath,
        roots = setOf(projectRootUri(firstProject)),
      ).resolveProject()

      assertThat(project).isEqualTo(secondProject)
    }
  }

  @Test
  fun `session header wins over roots`() {
    runBlocking(Dispatchers.Default) {
      val project = McpProjectLocationInputs(
        projectPathFromArgument = null,
        projectPathFromCallHeader = "/tmp/not-an-open-project",
        projectPathFromSessionHeader = secondProject.basePath,
        roots = setOf(projectRootUri(firstProject)),
      ).resolveProject()

      assertThat(project).isEqualTo(secondProject)
    }
  }

  @Test
  fun `roots are used as final fallback`() {
    runBlocking(Dispatchers.Default) {
      val project = McpProjectLocationInputs(
        projectPathFromArgument = null,
        projectPathFromCallHeader = "/tmp/not-an-open-project",
        projectPathFromSessionHeader = "/tmp/also-not-an-open-project",
        roots = setOf(projectRootUri(secondProject)),
      ).resolveProject()

      assertThat(project).isEqualTo(secondProject)
    }
  }

  @Test
  fun `chaining mode throws user-facing error without internal source details`() {
    assertThatThrownBy {
      runBlocking(Dispatchers.Default) {
        McpProjectLocationInputs(
          projectPathFromArgument = null,
          projectPathFromCallHeader = "/tmp/not-an-open-project",
          projectPathFromSessionHeader = "/tmp/also-not-an-open-project",
          roots = setOf("file:///tmp/missing-root"),
        ).resolveProject()
      }
    }
      .isInstanceOf(McpExpectedError::class.java)
      .satisfies({ throwable: Throwable ->
        val error = throwable as McpExpectedError
        assertThat(error.mcpErrorText).contains("You may specify the project path via `${projectPathParameterName}` parameter")
        assertThat(error.mcpErrorText).doesNotContain("header")
        assertThat(error.mcpErrorText).doesNotContain("env")
        assertThat(error.mcpErrorText).doesNotContain(IJ_MCP_SERVER_PROJECT_PATH)
      })
  }

  private fun projectRootUri(project: Project): String = Path.of(project.basePath!!).toUri().toString()
}
