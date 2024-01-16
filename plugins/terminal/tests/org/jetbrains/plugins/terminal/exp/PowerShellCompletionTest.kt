// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import com.intellij.testFramework.utils.io.deleteRecursively
import org.jetbrains.plugins.terminal.exp.completion.ShellCommandExecutor
import org.jetbrains.plugins.terminal.exp.completion.ShellCommandExecutorImpl
import org.jetbrains.plugins.terminal.exp.completion.powershell.PowerShellCompletionContributor
import org.jetbrains.plugins.terminal.exp.util.TerminalSessionTestUtil
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries

@RunWith(JUnit4::class)
class PowerShellCompletionTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {
  private lateinit var session: BlockTerminalSession

  override fun setUp() {
    Assume.assumeTrue("This test is supposed to be run only on Windows", SystemInfo.isWindows)
    val powerShellFile = listOf("powershell.exe", "pwsh.exe").firstNotNullOfOrNull { PathEnvironmentVariableUtil.findInPath(it) }
    Assume.assumeTrue("Powershell executable not found", powerShellFile != null)

    super.setUp()

    session = TerminalSessionTestUtil.startBlockTerminalSession(project, powerShellFile!!.absolutePath, testRootDisposable)
    // configure to test only powershell completion contributor
    val pluginDescriptor = DefaultPluginDescriptor(PluginId.findId("org.jetbrains.plugins.terminal")!!, javaClass.classLoader)
    val extension = CompletionContributorEP("any", PowerShellCompletionContributor::class.java.name, pluginDescriptor)
    ExtensionTestUtil.maskExtensions(CompletionContributor.EP, listOf(extension), testRootDisposable)
  }

  override fun tearDown() {
    if (!this::session.isInitialized) {
      return // no shell started and
    }
    try {
      Disposer.dispose(session)
    }
    catch (t: Throwable) {
      addSuppressedException(t)
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun `Complete command names`() {
    val completions = getCompletionsForCommand("pw<caret>")
    assertCompletionsContain(completions, "pwd")
  }

  @Test
  fun `Complete method names`() {
    val command = """
      "aaa".<caret>
    """.trimIndent()
    val completions = getCompletionsForCommand(command)
    assertCompletionsContain(completions, "Equals(", "Length", "GetType(")
  }

  @Test
  fun `Complete command options`() {
    val completions = getCompletionsForCommand("ls -<caret>")
    assertCompletionsContain(completions, "-Path", "-Recurse", "-File")
  }

  @Test
  fun `Complete files`() {
    val tempDirectory = createTempDir().apply {
      createFile("file.txt")
      createDirectory("dir")
      createFile(".hidden")
    }
    val completions = getCompletionsForCommand("ls $tempDirectory\\<caret>")
    val expected = tempDirectory.listDirectoryEntries().map { it.absolutePathString() }
    assertSameCompletions(completions, expected)
  }

  @Test
  fun `Complete command on second line`() {
    val command = """
      ls
      c<caret>
    """.trimIndent()
    val completions = getCompletionsForCommand(command)
    assertCompletionsContain(completions, "cd", "cp", "clear")
  }

  @Test
  fun `Complete command option on next line`() {
    val command = """
      cp `
      -<caret>
    """.trimIndent()
    val completions = getCompletionsForCommand(command)
    assertCompletionsContain(completions, "-Path", "-Destination", "-Filter")
  }

  @Test
  fun `Complete nothing`() {
    val completions = getCompletionsForCommand("<caret>ls Documents")
    assertTrue("Completions is not empty: $completions", completions.isNullOrEmpty())
  }

  @Test
  fun `Backticks are escaped properly`() {
    val command = """
      "normal string with ``backticks``".<caret>
    """.trimIndent()
    val completions = getCompletionsForCommand(command)
    assertCompletionsContain(completions, "Equals(")
  }

  @Test
  fun `Insert directory name with spaces`() {
    val dirName = "Dir with spaces"
    val tempDirectory = createTempDir().apply {
      createDirectory(dirName)
      createFile("someFile.txt")
    }
    val completions = getCompletionsForCommand("ls $tempDirectory\\<caret>")
    assertFalse("Completions are null or empty", completions.isNullOrEmpty())
    selectItemAndCheckResult(dirName, expectedText = "ls '$tempDirectory\\$dirName<caret>'")
  }

  @Test
  fun `Do not insert additional quote`() {
    val dirName = "Dir with spaces"
    val tempDirectory = createTempDir().apply {
      createDirectory(dirName)
      createFile("someFile.txt")
    }
    val completions = getCompletionsForCommand("ls '$tempDirectory\\<caret>'")
    assertFalse("Completions are null or empty", completions.isNullOrEmpty())
    selectItemAndCheckResult(dirName, expectedText = "ls '$tempDirectory\\$dirName<caret>'")
  }

  private fun getCompletionsForCommand(command: String): List<String>? {
    myFixture.configureByText(PlainTextFileType.INSTANCE, command)
    editor.putUserData(BlockTerminalSession.KEY, session)
    editor.putUserData(ShellCommandExecutor.KEY, ShellCommandExecutorImpl(session))
    myFixture.completeBasic()
    return myFixture.lookupElementStrings
  }

  private fun assertCompletionsContain(completions: List<String>?, vararg expected: String) {
    assertNotNull("Completions list is null", completions)
    assertTrue("No expected suggestion proposed. Completions: $completions", completions!!.containsAll(expected.toList()))
  }

  private fun assertSameCompletions(completions: List<String>?, expected: List<String>) {
    assertNotNull("Completions list is null", completions)
    assertSameElements("Expected $expected, but was: $completions", completions!!, expected)
  }

  private fun selectItemAndCheckResult(itemText: String, expectedText: String) {
    myFixture.lookup.currentItem = myFixture.lookupElements!!.find { it.lookupString.contains(itemText) }
    myFixture.finishLookup('\n')
    myFixture.checkResult(expectedText)
  }

  private fun createTempDir(): Path {
    return createTempDirectory().also {
      Disposer.register(testRootDisposable) { it.deleteRecursively() }
    }
  }
}