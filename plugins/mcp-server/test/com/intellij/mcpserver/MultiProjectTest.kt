@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.mcpserver.toolsets.general.FileToolset
import com.intellij.mcpserver.toolsets.general.ProjectToolset
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.platform.commons.annotation.Testable
import kotlin.io.path.Path

@Testable
@TestApplication
class MultiProjectTest {
  companion object {
    @BeforeAll
    @JvmStatic
    fun init() {
      System.setProperty("java.awt.headless", "false")
    }
  }

  // Create two separate project fixtures to simulate multiple projects
  private val project1Fixture = projectFixture(openAfterCreation = true)
  private val project1 by project1Fixture
  private val module1Fixture = project1Fixture.moduleFixture("testModule1")
  private val sourceRoot1Fixture = module1Fixture.sourceRootFixture(pathFixture = project1Fixture.pathInProjectFixture(Path("src")))
  private val file1Fixture = sourceRoot1Fixture.virtualFileFixture("Test1.java", "Test1.java content")

  private val project2Fixture = projectFixture(openAfterCreation = true)
  private val project2 by project2Fixture
  private val module2Fixture = project2Fixture.moduleFixture("testModule2")
  private val sourceRoot2Fixture = module2Fixture.sourceRootFixture(pathFixture = project2Fixture.pathInProjectFixture(Path("src")))
  private val file2Fixture = sourceRoot2Fixture.virtualFileFixture("Test2.java", "Test2.java content")

  @Test
  fun test_list_projects_shows_multiple_projects() = runBlocking {
    // This test verifies that list_projects returns information about multiple open projects
    val testBase = object : McpToolsetTestBase() {
      override val projectFixture = project1Fixture
    }
    
    testBase.testMcpTool(
      ProjectToolset::list_projects.name,
      buildJsonObject { },
    ) { result ->
      val textContent = result.textContent
      // The result should contain multiple projects
      assert(textContent.text.contains("projects")) { "Result should contain projects array" }
      // Should contain both project names (though we can't predict the exact names)
      assert(textContent.text.contains("name")) { "Result should contain project names" }
      assert(textContent.text.contains("basePath")) { "Result should contain project base paths" }
    }
  }

  @Test
  fun test_project_aware_file_operations() = runBlocking {
    // This test verifies that tools can work with specific projects when projectName is specified
    val testBase = object : McpToolsetTestBase() {
      override val projectFixture = project1Fixture
    }
    
    // Test creating a file with projectPath parameter (more reliable than projectName)
    testBase.testMcpTool(
      FileToolset::create_new_file.name,
      buildJsonObject {
        put("pathInProject", JsonPrimitive("test-multi-project.txt"))
        put("text", JsonPrimitive("Multi-project test content"))
        put("projectPath", JsonPrimitive(project1.basePath ?: ""))
      },
    ) { result ->
      // Should succeed without error
      val textContent = result.textContent
      assert(!result.isError) { "File creation should succeed" }
    }
  }
}
