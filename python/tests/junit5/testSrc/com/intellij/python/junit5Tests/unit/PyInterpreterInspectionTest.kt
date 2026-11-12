// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.python.junit5Tests.framework.rootSourceRootFixture
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.tools.sdkTools.PythonMockSdk
import com.jetbrains.python.sdk.inspections.PyInterpreterNotificationProvider
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.setPythonSdkForTests
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@TestApplication
class PyInterpreterInspectionTest {
  private val testDisposable by disposableFixture()
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val pyProjectFixture = projectFixture.pyProjectFixture()
  private val sourceRootFixture = pyProjectFixture.rootSourceRootFixture()

  private val project get() = projectFixture.get()
  private val module get() = pyProjectFixture.get().residesOnModule

  @BeforeEach
  fun setUp() {
    sourceRootFixture.get()
    // No real configurator probes the machine. The fake one still claims pyproject.toml, which makes that file relevant.
    ExtensionTestUtil.maskExtensions(
      PyProjectSdkConfigurationExtension.EP_NAME,
      listOf(PyProjectTomlOnlyConfigurator),
      testDisposable,
    )
  }

  @Test
  fun `no interpreter configured shows notification`() {
    assertNotificationShown("test.py", "print('hello')\n")
  }

  @Test
  fun `no interpreter configured shows notification in empty file`() {
    assertNotificationShown("__init__.py", "")
  }

  @Test
  fun `no interpreter configured shows notification for pyproject toml`() {
    assertNotificationShown("pyproject.toml", "[project]\nname = \"test\"\n")
  }

  @Test
  fun `no interpreter configured shows notification for README`() {
    // README.md notification requires Python files in the module
    createFileInModule("main.py", "")
    assertNotificationShown("README.md", "# Test\n")
  }

  @Test
  fun `no notification for README without python files`(): Unit = timeoutRunBlocking {
    val file = createFileInModule("README.md", "# Test\n")
    val provider = PyInterpreterNotificationProvider()
    readAction {
      assertNull(provider.collectNotificationData(project, file), "Expected no notification for 'README.md' without Python files in module")
    }
  }

  @Test
  fun `no notification when sdk configured`(): Unit = timeoutRunBlocking {
    module.setPythonSdkForTests(PythonMockSdk.create())
    val file = createFileInModule("test.py", "print('hello')\n")
    val provider = PyInterpreterNotificationProvider()
    readAction {
      assertNull(provider.collectNotificationData(project, file), "Expected no notification when SDK is configured")
    }
  }

  @Test
  fun `no notification when sdk configured for pyproject toml`(): Unit = timeoutRunBlocking {
    module.setPythonSdkForTests(PythonMockSdk.create())
    val file = createFileInModule("pyproject.toml", "[project]\nname = \"test\"\n")
    val provider = PyInterpreterNotificationProvider()
    readAction {
      assertNull(provider.collectNotificationData(project, file), "Expected no notification for 'pyproject.toml' when SDK is configured")
    }
  }

  @Test
  fun `no notification for non-python module`(): Unit = timeoutRunBlocking {
    module.setModuleType("JAVA_MODULE")
    val file = createFileInModule("test.py", "print('hello')\n")
    val provider = PyInterpreterNotificationProvider()
    readAction {
      assertNull(provider.collectNotificationData(project, file), "Expected no notification for non-Python module")
    }
  }

  @Test
  fun `no notification for irrelevant file`(): Unit = timeoutRunBlocking {
    val file = createFileInModule("build.gradle", "")
    val provider = PyInterpreterNotificationProvider()
    readAction {
      assertNull(provider.collectNotificationData(project, file), "Expected no notification for 'build.gradle'")
    }
  }

  private fun assertNotificationShown(fileName: String, content: String): Unit = timeoutRunBlocking {
    val file = createFileInModule(fileName, content)
    val provider = PyInterpreterNotificationProvider()
    waitUntilAssertSucceeds(timeout = 30.seconds) {
      readAction {
        assertNotNull(provider.collectNotificationData(project, file), "Expected notification for '$fileName' when no SDK is configured")
      }
    }
  }

  /** Claims pyproject.toml and offers no environment, so it runs no tool. */
  private object PyProjectTomlOnlyConfigurator : PyProjectSdkConfigurationExtension {
    override val toolId: ToolId = ToolId("pyproject-toml-only-test-tool")
    override val potentialDependencyFiles: Set<String> = setOf(PY_PROJECT_TOML)
    override suspend fun checkEnvironmentAndPrepareSdkCreator(pyProject: PyProject, venvs: List<PythonBinary>): CreateInterpreterInfo? = null
    override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null
  }

  private fun createFileInModule(fileName: String, content: String): VirtualFile {
    val moduleRoot = module.guessModuleDir()?.toNioPath() ?: error("Module root not found")
    val filePath = moduleRoot.resolve(fileName)
    filePath.writeText(content)
    return VirtualFileManager.getInstance().refreshAndFindFileByNioPath(filePath)!!
  }
}
