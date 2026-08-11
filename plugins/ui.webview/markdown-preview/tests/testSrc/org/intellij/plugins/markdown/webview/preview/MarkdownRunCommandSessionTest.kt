// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.webview.preview

import com.intellij.execution.Executor
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingProviderBase
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.MarkdownRunner
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider
import org.intellij.plugins.markdown.injection.aliases.AdditionalFenceLanguageSuggester
import org.intellij.plugins.markdown.settings.MarkdownExtensionsSettings

internal class MarkdownRunCommandSessionTest : BasePlatformTestCase() {
  private val runAnythingProvider = TestRunAnythingProvider()
  private val markdownRunner = TestMarkdownRunner()

  override fun setUp() {
    super.setUp()
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(
      MarkdownExtensionsSettings::class.java,
      MarkdownExtensionsSettings(),
      testRootDisposable,
    )
    registerMissingExtensionPoint(MarkdownRunner.EP_NAME.name, MarkdownRunner::class.java.name)
    registerMissingExtensionPoint(CodeFenceLanguageProvider.EP_NAME.name, CodeFenceLanguageProvider::class.java.name)
    registerMissingExtensionPoint(ADDITIONAL_FENCE_LANGUAGE_SUGGESTER_EP, AdditionalFenceLanguageSuggester::class.java.name)
    ExtensionTestUtil.maskExtensions(MarkdownRunner.EP_NAME, listOf(markdownRunner), testRootDisposable)
    ExtensionTestUtil.maskExtensions(RunAnythingProvider.EP_NAME, listOf(runAnythingProvider), testRootDisposable)
  }

  private fun registerMissingExtensionPoint(name: String, beanClassName: String) {
    val extensionArea = ApplicationManager.getApplication().extensionArea
    if (extensionArea.hasExtensionPoint(name)) return

    extensionArea.registerExtensionPoint(name, beanClassName, ExtensionPoint.Kind.INTERFACE, false)
    Disposer.register(testRootDisposable) {
      extensionArea.unregisterExtensionPoint(name)
    }
  }

  fun `test shell code fence candidates create block and line command descriptors`() {
    runAnythingProvider.commands = setOf("pwd", "npm test")
    val firstLine = lineCandidate(id = "line:pwd", line = 2, rawCommand = "pwd")
    val session = resolveSession(
      listOf(
        blockCandidate(firstLineCommandId = firstLine.id),
        firstLine,
        lineCandidate(id = "line:npm-test", line = 3, rawCommand = "npm test"),
      )
    )

    val blockCommand = session.descriptors.single { it.kind == MarkdownPreviewCommandKind.BLOCK }
    val lineCommands = session.descriptors.filter { it.kind == MarkdownPreviewCommandKind.LINE }

    assertEquals(1, blockCommand.startLine)
    assertEquals(2, lineCommands.size)
    assertEquals(listOf(2, 3), lineCommands.map { it.startLine })
    assertEquals(lineCommands.first().id, blockCommand.firstLineCommandId)
  }

  fun `test inline code creates inline command descriptor`() {
    runAnythingProvider.commands = setOf("npm test")
    val session = resolveSession(
      listOf(
        inlineCandidate(),
      )
    )

    val inlineCommand = session.descriptors.single { it.kind == MarkdownPreviewCommandKind.INLINE }

    assertEquals(1, inlineCommand.startLine)
    assertEquals("Run 'npm test'", inlineCommand.title)
  }

  fun `test unknown command id is not resolved`() {
    runAnythingProvider.commands = setOf("pwd")
    val session = resolveSession(
      listOf(
        blockCandidate(firstLineCommandId = null),
        lineCandidate(id = "line:pwd", line = 2, rawCommand = "pwd"),
      )
    )

    assertNull(session.command("missing-command"))
  }

  private fun resolveSession(candidates: List<MarkdownCommandCandidate>): MarkdownRunCommandSession {
    return MarkdownRunCommandSession.resolve(project, null, candidates)
  }

  private fun blockCandidate(firstLineCommandId: String?): MarkdownCommandCandidate {
    return MarkdownCommandCandidate(
      id = "block:shell",
      kind = MarkdownPreviewCommandKind.BLOCK,
      startLine = 1,
      startColumn = 1,
      endLine = 4,
      endColumn = 4,
      rawCommand = "pwd\nnpm test\n",
      language = "shell",
      firstLineCommandId = firstLineCommandId,
    )
  }

  private fun lineCandidate(id: String, line: Int, rawCommand: String): MarkdownCommandCandidate {
    return MarkdownCommandCandidate(
      id = id,
      kind = MarkdownPreviewCommandKind.LINE,
      startLine = line,
      startColumn = 1,
      endLine = line,
      endColumn = rawCommand.length + 1,
      rawCommand = rawCommand,
    )
  }

  private fun inlineCandidate(): MarkdownCommandCandidate {
    return MarkdownCommandCandidate(
      id = "inline:npm-test",
      kind = MarkdownPreviewCommandKind.INLINE,
      startLine = 1,
      startColumn = 5,
      endLine = 1,
      endColumn = 15,
      rawCommand = "npm test",
    )
  }

  private class TestRunAnythingProvider : RunAnythingProviderBase<String>() {
    var commands: Set<String> = emptySet()

    override fun getValues(dataContext: DataContext, pattern: String): Collection<String> {
      return commands.filter { it == pattern }
    }

    override fun execute(dataContext: DataContext, value: String) {
    }

    override fun getCommand(value: String): String = value
  }

  private class TestMarkdownRunner : MarkdownRunner {
    override fun isApplicable(language: Language?): Boolean = true

    override fun run(command: String, project: Project, workingDirectory: String?, executor: Executor): Boolean = true

    override fun title(): String = "Run block"
  }

  private companion object {
    private const val ADDITIONAL_FENCE_LANGUAGE_SUGGESTER_EP = "org.intellij.markdown.additionalFenceLanguageSuggester"
  }
}
