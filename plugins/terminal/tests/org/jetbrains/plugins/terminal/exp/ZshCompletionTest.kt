// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.writeText

@RunWith(JUnit4::class)
class ZshCompletionTest : BaseShCompletionTest() {
  override val shellPath: String = "/bin/zsh"

  override fun createCompletionManager(session: TerminalSession): TerminalCompletionManager {
    return ZshCompletionManager(session)
  }

  @Test
  fun `complete and get many items with spaces`() {
    testDirectory.run {
      createFile("ab cde")
      createFile("bcd e")
      createFile("agh sd ml")
      createDirectory("acde")
      createFile("aeufsgf")
      createDirectory("cedr tuysa")
      createDirectory("aft yrt")
    }

    val elements = typeLsAndComplete("a")

    assertNotNull(elements)
    assertSameElements(elements!!.map { it.lookupString }, "ab\\ cde", "agh\\ sd\\ ml", "acde/", "aeufsgf", "aft\\ yrt/")
    assertPromptRestored()
  }

  @Test
  fun `complete and get a lot of parameters with descriptions`() {
    val arguments = listOf(CompletionItem("-a", "Some description"),
                           CompletionItem("-B", "some other desc"),
                           CompletionItem("-c"),
                           CompletionItem("-long", "desc"),
                           CompletionItem("--a", "aaabbbccc"),
                           CompletionItem("--abcd"),
                           CompletionItem("--asdasd", "descdesc"))
    val commandName = "completiontest"
    configureParametersCompletion(commandName, arguments)

    myFixture.type("$commandName -")
    val elements = myFixture.completeBasic()

    assertTrue(elements.isNotEmpty())
    assertSameElements(elements.map { it.toCompletionItem() }, arguments)
    assertPromptRestored()
  }

  @Test
  fun `complete and get a lot of parameters without descriptions`() {
    val arguments = listOf(CompletionItem("-a"),
                           CompletionItem("-B"),
                           CompletionItem("-c"),
                           CompletionItem("-long"),
                           CompletionItem("--a"),
                           CompletionItem("--abcd"),
                           CompletionItem("--asdasd"))
    val commandName = "completiontest"
    configureParametersCompletion(commandName, arguments)

    myFixture.type("$commandName -")
    val elements = myFixture.completeBasic()

    assertTrue(elements.isNotEmpty())
    assertSameElements(elements.map { it.toCompletionItem() }, arguments)
    assertPromptRestored()
  }

  @Test
  fun `complete and get a single parameter autocompleted`() {
    val arguments = listOf(CompletionItem("-a", "some description"))
    val commandName = "completiontest"
    configureParametersCompletion(commandName, arguments)

    myFixture.type("$commandName -")
    val elements = myFixture.completeBasic()

    assertSingleItemCompleted(elements, "-a")
    assertPromptRestored()
  }

  private fun configureParametersCompletion(commandName: String, arguments: List<CompletionItem>) {
    val completionFilesDir = testDirectory.createDirectory("compl")
    completionFilesDir.createFile("_$commandName").writeText("""
      #compdef ${commandName}
      local arguments=(
        ${arguments.joinToString("\n") { it.toCompletionArgument() }}
      )
      _arguments : ${"$"}arguments
    """.trimIndent())

    val disposable = Disposer.newDisposable()
    val future = CompletableFuture<Boolean>()
    session.addCommandListener(object : ShellCommandListener {
      override fun commandFinished(command: String, exitCode: Int, duration: Long) {
        future.complete(true)
      }
    }, disposable)

    val command = """ fpath=("$completionFilesDir" ${"$"}fpath) && autoload -Uz compinit && compinit"""
    session.executeCommand(command)
    val model = session.model

    try {
      future.get(5000, TimeUnit.MILLISECONDS)
    }
    catch (ex: TimeoutException) {
      fail("Session failed to finish command. Text buffer:\n${model.withContentLock { model.getAllText() }}")
    }
    finally {
      Disposer.dispose(disposable)
    }

    model.clearAllExceptPrompt(1)
  }

  private data class CompletionItem(val value: String, val description: String? = null)

  private fun CompletionItem.toCompletionArgument(): String {
    val desc = description?.let { "[$it]" } ?: ""
    return "'$value$desc'"
  }

  private fun LookupElement.toCompletionItem(): CompletionItem {
    val presentation = LookupElementPresentation()
    renderElement(presentation)
    val value = presentation.itemText ?: error("No item text for $this")
    return CompletionItem(value, presentation.typeText)
  }
}