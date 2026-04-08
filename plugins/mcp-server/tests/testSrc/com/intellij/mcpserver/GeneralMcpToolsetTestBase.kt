@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.fileOrDirInProjectFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path

abstract class GeneralMcpToolsetTestBase : McpToolsetTestBase() {
  companion object {
    private const val TEST_PROJECT_RESOURCE_PATH = "community/plugins/mcp-server/tests/testResources/mcpToolsetProject"
  }

  override fun projectTestData(): Path? = Path.of(TEST_PROJECT_RESOURCE_PATH)

  protected open fun sourceRootRelativePath(): Path = Path.of("src")

  protected open val moduleFixture: TestFixture<Module> = projectFixture.moduleFixture("testModule")

  protected open val sourceRootFixture: TestFixture<PsiDirectory> =
    moduleFixture.sourceRootFixture(
      pathFixture = projectFixture.pathInProjectFixture(sourceRootRelativePath()),
    )

  @BeforeEach
  fun initSourceRootFixture() {
    sourceRootFixture.get()
  }

  /**
   * Convenience handle for the shared sample project's `src/Main.java`.
   */
  protected val mainJavaFile: VirtualFile by projectFixture.fileOrDirInProjectFixture("src/Main.java")

  /**
   * Convenience handle for the shared sample project's `src/Test.java`.
   */
  protected val testJavaFile: VirtualFile by projectFixture.fileOrDirInProjectFixture("src/Test.java")

  /**
   * Convenience handle for the shared sample project's `src/Class.java`.
   */
  protected val classJavaFile: VirtualFile by projectFixture.fileOrDirInProjectFixture("src/Class.java")
}
