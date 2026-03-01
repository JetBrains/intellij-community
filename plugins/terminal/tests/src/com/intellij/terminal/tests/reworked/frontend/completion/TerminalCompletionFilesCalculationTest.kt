package com.intellij.terminal.tests.reworked.frontend.completion

import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.terminal.completion.spec.ShellFileInfo
import com.intellij.terminal.completion.spec.ShellFileInfo.Type.DIRECTORY
import com.intellij.terminal.completion.spec.ShellFileInfo.Type.FILE
import com.intellij.terminal.frontend.view.completion.ShellCommandExecutorReworked
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.util.SystemProperties
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.completion.spec.IS_REWORKED_KEY
import org.jetbrains.plugins.terminal.block.completion.spec.getChildFiles
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellDataGeneratorProcessExecutorImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellFileSystemSupportImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellRuntimeContextImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.name

/**
 * Test for [org.jetbrains.plugins.terminal.block.completion.spec.getChildFiles] in Reworked Terminal.
 */
@RunWith(JUnit4::class)
internal class TerminalCompletionFilesCalculationTest : BasePlatformTestCase() {
  @Test
  fun `get child files by absolute path`() {
    val root = createTempDirStructure()
    val actualFiles = getChildFiles(root.toString(), root.toString())
    val expectedFiles = listOf(
      fileInfo("file1.txt", FILE),
      fileInfo(".file2", FILE),
      fileInfo("file with spaces", FILE),
      fileInfo("dir1", DIRECTORY),
      fileInfo("dir with spaces", DIRECTORY),
      fileInfo("dir2", DIRECTORY),
    )

    assertThat(actualFiles).hasSameElementsAs(expectedFiles)
  }

  @Test
  fun `get child files by relative path`() {
    val root = createTempDirStructure()
    val actualFiles = getChildFiles(root.toString(), "")
    val expectedFiles = listOf(
      fileInfo("file1.txt", FILE),
      fileInfo(".file2", FILE),
      fileInfo("file with spaces", FILE),
      fileInfo("dir1", DIRECTORY),
      fileInfo("dir with spaces", DIRECTORY),
      fileInfo("dir2", DIRECTORY),
    )

    assertThat(actualFiles).hasSameElementsAs(expectedFiles)
  }

  @Test
  fun `get child files from nested dir by a relative path`() {
    val root = createTempDirStructure()
    val actualFiles = getChildFiles(root.toString(), "dir2")
    val expectedFiles = listOf(
      fileInfo("nestedFile1.txt", FILE),
      fileInfo(".nestedFile2", FILE),
      fileInfo("nestedFile with spaces", FILE),
      fileInfo("nestedDir", DIRECTORY),
    )

    assertThat(actualFiles).hasSameElementsAs(expectedFiles)
  }

  @Test
  fun `get child files from nested dir by a relative path with trailing path separator`() {
    val root = createTempDirStructure()
    val actualFiles = getChildFiles(root.toString(), "dir2" + File.separator)
    val expectedFiles = listOf(
      fileInfo("nestedFile1.txt", FILE),
      fileInfo(".nestedFile2", FILE),
      fileInfo("nestedFile with spaces", FILE),
      fileInfo("nestedDir", DIRECTORY),
    )

    assertThat(actualFiles).hasSameElementsAs(expectedFiles)
  }

  @Test
  fun `get child files from home related path`() {
    val tempFile = createTempFileInUserHome()
    val actualFiles = getChildFiles("/", "~")
    assertThat(actualFiles).contains(fileInfo(tempFile.name, FILE))
  }

  @Test
  fun `get child files from home related path with trailing path separator`() {
    val tempFile = createTempFileInUserHome()
    val actualFiles = getChildFiles("/", "~" + File.separator)
    assertThat(actualFiles).contains(fileInfo(tempFile.name, FILE))
  }

  private fun getChildFiles(currentDirectory: String, path: String): List<ShellFileInfo> = runBlocking {
    val eelDescriptor = LocalEelDescriptor
    val processExecutor = ShellDataGeneratorProcessExecutorImpl(eelDescriptor, baseEnvVariables = emptyMap())
    val context = ShellRuntimeContextImpl(
      currentDirectory = currentDirectory,
      envVariables = emptyMap(),
      commandTokens = listOf(path),
      definedShellName = null,
      generatorCommandsRunner = ShellCommandExecutorReworked(processExecutor),
      generatorProcessExecutor = processExecutor,
      fileSystemSupport = ShellFileSystemSupportImpl(eelDescriptor)
    )
    context.putUserData(IS_REWORKED_KEY, true)
    context.getChildFiles(path)
  }

  private fun createTempDirStructure(): Path {
    val root = createTempDirectory("terminal-completion")
    Disposer.register(testRootDisposable) { root.deleteRecursively() }
    return root.apply {
      createFile("file1.txt")
      createFile(".file2")
      createFile("file with spaces")
      createDirectory("dir1")
      createDirectory("dir with spaces")
      createDirectory("dir2").apply {
        createFile("nestedFile1.txt")
        createFile(".nestedFile2")
        createFile("nestedFile with spaces")
        createDirectory("nestedDir")
      }
    }
  }

  private fun createTempFileInUserHome(): Path {
    val userHome = Path(SystemProperties.getUserHome())
    val tempFile = userHome.createFile("terminal-completion-test-file")
    Disposer.register(testRootDisposable) { tempFile.deleteIfExists() }
    return tempFile
  }

  private fun fileInfo(name: String, type: ShellFileInfo.Type): ShellFileInfo {
    return ShellFileInfo.create(name, type)
  }
}