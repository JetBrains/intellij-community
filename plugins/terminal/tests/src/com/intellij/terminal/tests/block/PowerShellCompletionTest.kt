// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.block

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.tests.block.util.TerminalSessionTestUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import com.intellij.testFramework.utils.io.deleteRecursively
import org.jetbrains.plugins.terminal.block.completion.powershell.PowerShellCompletionContributor
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellDataGeneratorsExecutorImpl
import org.jetbrains.plugins.terminal.block.completion.spec.impl.ShellRuntimeContextProviderImpl
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptModel
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptModelImpl
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

@RunWith(JUnit4::class)
internal class PowerShellCompletionTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {
  private lateinit var session: BlockTerminalSession

  private val separator: Char = File.separatorChar

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
  fun `Complete files after typing OS path separator`() {
    val tempDirectory = createTempDir().apply {
      createFile("file.txt")
      createDirectory("dir")
      createFile(".hidden")
    }
    val completions = getCompletionsForCommand("ls $tempDirectory$separator<caret>")
    val expected = tempDirectory.listDirectoryEntries().map { it.name }
    assertSameCompletions(completions, expected)
  }

  @Test
  fun `Complete files after typing opposite path separator`() {
    val tempDirectory = createTempDir().apply {
      createDirectory("dir").apply {
        createFile("file.txt")
        createDirectory("subDir")
        createFile(".hidden")
      }
    }
    val oppositeSeparator = if (separator == '/') '\\' else '/'
    val completions = getCompletionsForCommand("ls $tempDirectory${separator}dir$oppositeSeparator<caret>")
    val expected = tempDirectory.resolve("dir").listDirectoryEntries().map { it.name }
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
    val completions = getCompletionsForCommand("ls $tempDirectory$separator<caret>")
    assertFalse("Completions are null or empty", completions.isNullOrEmpty())
    selectItemAndCheckResult(dirName, expectedText = "ls '$tempDirectory$separator$dirName$separator<caret>'")
  }

  @Test
  fun `Do not insert additional quote`() {
    val dirName = "Dir with spaces"
    val tempDirectory = createTempDir().apply {
      createDirectory(dirName)
      createFile("someFile.txt")
    }
    val completions = getCompletionsForCommand("ls '$tempDirectory$separator<caret>'")
    assertFalse("Completions are null or empty", completions.isNullOrEmpty())
    selectItemAndCheckResult(dirName, expectedText = "ls '$tempDirectory$separator$dirName$separator<caret>'")
  }

  private fun getCompletionsForCommand(command: String): List<String>? {
    myFixture.configureByText(PlainTextFileType.INSTANCE, command)
    editor.putUserData(BlockTerminalSession.KEY, session)

    val promptModel = TerminalPromptModelImpl(editor as EditorEx, session)
    Disposer.register(session, promptModel)
    editor.putUserData(TerminalPromptModel.KEY, promptModel)

    editor.putUserData(ShellRuntimeContextProviderImpl.KEY, ShellRuntimeContextProviderImpl(project, session))
    editor.putUserData(ShellDataGeneratorsExecutorImpl.KEY, ShellDataGeneratorsExecutorImpl(session))

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

  @Suppress("SameParameterValue")
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

