// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.application.readAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonMockSdk
import com.jetbrains.python.inspections.interpreter.PyInterpreterNotificationProvider
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.pythonSdk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@TestApplication
class PyInterpreterInspectionTest {
  private val testDisposable by disposableFixture()
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val moduleFixture = projectFixture.moduleFixture(moduleType = PyNames.PYTHON_MODULE_ID)
  private val sourceRootFixture = moduleFixture.sourceRootFixture(
    pathFixture = projectFixture.pathInProjectFixture(Path.of("")),
  )

  private val project get() = projectFixture.get()
  private val module get() = moduleFixture.get()

  @BeforeEach
  fun setUp() {
    sourceRootFixture.get()
    ExtensionTestUtil.maskExtensions(
      ExtensionPointName.create("Pythonid.projectSdkConfigurationExtension"),
      emptyList<PyProjectSdkConfigurationExtension>(),
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
    module.pythonSdk = PythonMockSdk.create()
    val file = createFileInModule("test.py", "print('hello')\n")
    val provider = PyInterpreterNotificationProvider()
    readAction {
      assertNull(provider.collectNotificationData(project, file), "Expected no notification when SDK is configured")
    }
  }

  @Test
  fun `no notification when sdk configured for pyproject toml`(): Unit = timeoutRunBlocking {
    module.pythonSdk = PythonMockSdk.create()
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

  private fun createFileInModule(fileName: String, content: String): VirtualFile {
    val moduleRoot = module.guessModuleDir()?.toNioPath() ?: error("Module root not found")
    val filePath = moduleRoot.resolve(fileName)
    filePath.writeText(content)
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath)!!
  }
}
