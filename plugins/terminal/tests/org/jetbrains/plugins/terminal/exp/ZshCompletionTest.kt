// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import com.jediterm.core.util.TermSize
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

@RunWith(JUnit4::class)
class ZshCompletionTest : BasePlatformTestCase() {
  private lateinit var session: TerminalSession
  private lateinit var testDirectory: Path

  override fun setUp() {
    Assume.assumeTrue(File("/bin/zsh").exists())
    super.setUp()

    Registry.get(LocalTerminalDirectRunner.BLOCK_TERMINAL_REGISTRY).setValue(true, testRootDisposable)
    session = startTerminalSession(TermSize(200, 20))
    val completionManager = TerminalCompletionManager(session)
    val model = session.model

    myFixture.configureByText(FileTypes.PLAIN_TEXT, "")
    myFixture.editor.putUserData(TerminalSession.KEY, session)
    myFixture.editor.putUserData(TerminalCompletionManager.KEY, completionManager)

    testDirectory = createTempDirectory(prefix = "zsh_completion")

    LOG.info("Temp directory path: $testDirectory")
    LOG.info("Initial terminal state:\n${model.withContentLock { model.getAllText() }}")
  }

  override fun tearDown() {
    if (!this::session.isInitialized) {
      return // no zsh installed
    }
    try {
      val model: TerminalModel = session.model
      LOG.info("Final terminal state:\n${model.withContentLock { model.getAllText() }}")
      Disposer.dispose(session)
      FileUtil.deleteRecursively(testDirectory)
    }
    catch (t: Throwable) {
      addSuppressedException(t)
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun `complete and get multiple options`() {
    multipleOptionsTest()
  }

  @Test
  fun `complete and get multiple options (small width)`() {
    session.postResize(TermSize(35, 20))
    multipleOptionsTest()
  }

  private fun multipleOptionsTest() {
    testDirectory.run {
      createFile("abcde")
      createFile("bcde")
      createFile("aghsdml")
      createDirectory("acde")
      createFile("aeufsgf")
      createDirectory("cedrtuysa")
      createDirectory("aftyrt")
    }

    val elements = typeLsAndComplete("a")

    assertNotNull(elements)
    assertSameElements(elements!!.map { it.lookupString }, "abcde", "aghsdml", "acde/", "aeufsgf", "aftyrt/")
    assertPromptRestored()
  }

  @Test
  fun `complete and get one option autocompleted`() {
    autocompleteOneOptionTest()
  }

  @Test
  fun `complete and get one option autocompleted (small width)`() {
    session.postResize(TermSize(35, 20))
    autocompleteOneOptionTest()
  }

  private fun autocompleteOneOptionTest() {
    testDirectory.run {
      createFile("abcde")
      createFile("bcde")
      createDirectory("acde")
      createFile("aeufsgf")
      createDirectory("cedrtuysa")
    }

    val elements = typeLsAndComplete("ac")

    assertTrue(elements.isNullOrEmpty())
    assertTrue(myFixture.editor.document.text.endsWith("acde/"))
    assertPromptRestored()
  }

  @Test
  fun `complete full names instead of common prefix`() {
    completeFullNamesTest()
  }

  @Test
  fun `complete full names instead of common prefix (small width)`() {
    session.postResize(TermSize(35, 20))
    completeFullNamesTest()
  }

  private fun completeFullNamesTest() {
    testDirectory.run {
      createFile("abcde")
      createFile("bcde")
      createDirectory("abc")
      createFile("abcfsgf")
      createDirectory("cedrtuysa")
    }

    val elements = typeLsAndComplete("a")

    assertNotNull(elements)
    assertSameElements(elements!!.map { it.lookupString }, "abcde", "abc/", "abcfsgf")
    assertPromptRestored()
  }

  @Test
  fun `complete and get a lot of options`() {
    manyOptionsTest()
  }

  @Test
  fun `complete and get a lot of options (small width)`() {
    session.postResize(TermSize(40, 35))
    manyOptionsTest()
  }

  @Test
  fun `complete and get a lot of options (small height)`() {
    session.postResize(TermSize(200, 3))
    manyOptionsTest()
  }

  @Test
  fun `complete and get a lot of options (small width and height)`() {
    session.postResize(TermSize(40, 10))
    manyOptionsTest()
  }

  private fun manyOptionsTest() {
    val fileNames = listOf("abcde", "bcde", "acde", "aeufsgf", "adetr", "asdgtw", "dfvre", "arsdfsgsd", "amnsdf", "asnsdg",
                           "amoiofr", "anonvreo", "cnierncier", "antreefer", "amovmrtov", "ampvmrtp", "avsdgerg", "avereterer",
                           "anveruinvr", "abcerbgyuer", "abuvbrvr", "amrtmvrtvr", "srervnerio", "pelmen", "avtuhuief", "sdgerg",
                           "aomvioer", "neovner", "ampvmrepo", "amvier", "anivbritr", "ioericre", "afherwef", "ancreedsgsd",
                           "asdgersgd", "averiubvuer", "aveurviuer", "abtrobrt", "atbrtbvtri", "awcrucbuier", "avorntovrnt",
                           "abrtuibtruib", "avruievhreuv", "aveuyveryuv", "aweyceycyu", "artboprtbprt", "aeyeruvbre",
                           "amverovner", "fvervuiren", "abrtbrotb", "abiurnbuirt", "abntriubrtib", "avueruvberuv", "aeryeryoerjy",
                           "avuhvueirvieruv", "apokprvp")

    testDirectory.run {
      fileNames.forEach { createFile(it) }
    }

    val elements = typeLsAndComplete("a")

    assertNotNull(elements)
    assertSameElements(elements!!.map { it.lookupString }, fileNames.filter { it.startsWith("a") })
    assertPromptRestored()
  }

  @Test
  fun `complete multiline command and get multiple options`() {
    testDirectory.run {
      createFile("abcde")
      createFile("bcde")
      createFile("aghsdml")
      createDirectory("acde")
      createFile("aeufsgf")
      createDirectory("cedrtuysa")
      createDirectory("aftyrt")
    }

    val cmdBuilder = StringBuilder("ls ${testDirectory}/a")
    cmdBuilder.insert(cmdBuilder.length / 3, "\\\n")
    cmdBuilder.insert((cmdBuilder.length / 3) * 2, "\\\n")
    myFixture.type(cmdBuilder.toString())
    val elements = myFixture.completeBasic()

    assertNotNull(elements)
    assertSameElements(elements!!.map { it.lookupString }, "abcde", "aghsdml", "acde/", "aeufsgf", "aftyrt/")
    assertPromptRestored()
  }

  @Test
  fun `complete and get item with spaces autocompleted`() {
    testDirectory.run {
      createDirectory("abc de")
      createFile("bcde")
      createFile("aghsdm l")
      createDirectory("ac d e")
      createFile("aeufsgf")
      createDirectory("ced  rtuysa")
      createDirectory("aftyrt")
    }

    val elements = typeLsAndComplete("ab")

    assertTrue(elements.isNullOrEmpty())
    assertTrue(myFixture.editor.document.text.endsWith("abc\\ de/"))
    assertPromptRestored()
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
  fun `complete and get no items`() {
    testDirectory.run {
      createDirectory("abcde")
      createFile("bcde")
      createFile("aeufsgf")
      createDirectory("aftyrt")
    }

    val elements = typeLsAndComplete("da")

    assertTrue(elements.isNullOrEmpty())
    assertTrue(myFixture.editor.document.text.endsWith("da"))
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
    UsefulTestCase.assertSameElements(elements.map { it.toCompletionItem() }, arguments)
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
    UsefulTestCase.assertSameElements(elements.map { it.toCompletionItem() }, arguments)
    assertPromptRestored()
  }

  @Test
  fun `complete and get a single parameter autocompleted`() {
    val arguments = listOf(CompletionItem("-a", "some description"))
    val commandName = "completiontest"
    configureParametersCompletion(commandName, arguments)

    myFixture.type("$commandName -")
    val elements = myFixture.completeBasic()

    assertTrue(elements.isNullOrEmpty())
    assertTrue(myFixture.editor.document.text.endsWith("-a"))
    assertPromptRestored()
  }

  private fun typeLsAndComplete(prefix: String): Array<LookupElement>? {
    myFixture.type("ls ${testDirectory}/$prefix")
    return myFixture.completeBasic()
  }

  private fun assertPromptRestored() {
    val model = session.model
    if (model.commandExecutionSemaphore.waitFor(2000)) {
      val restored = model.withContentLock { model.getAllText().isEmpty() }
      assertTrue(restored)
    }
    else {
      fail("Failed to acquire command execution lock, seems that prompt is broken")
    }
  }

  private fun startTerminalSession(size: TermSize): TerminalSession {
    val runner = LocalTerminalDirectRunner.createTerminalRunner(project)
    val baseOptions = ShellStartupOptions.Builder().shellCommand(listOf("/bin/zsh", "-i")).build()
    val configuredOptions = runner.configureStartupOptions(baseOptions)
    val process = runner.createProcess(configuredOptions)
    val ttyConnector = runner.createTtyConnector(process)

    val session = TerminalSession(runner.settingsProvider)
    session.shellIntegration = configuredOptions.shellIntegration
    val model: TerminalModel = session.model

    val future = CompletableFuture<Boolean>()
    val listenerDisposable = Disposer.newDisposable()
    session.addCommandListener(object : ShellCommandListener {
      override fun promptShown() {
        future.complete(true)
      }
    }, listenerDisposable)

    session.start(ttyConnector)
    session.postResize(size)

    try {
      future.get(5000, TimeUnit.MILLISECONDS)
    }
    catch (ex: TimeoutException) {
      fail("Session failed to initialize. Text buffer:\n${model.withContentLock { model.getAllText() }}")
    }
    finally {
      Disposer.dispose(listenerDisposable)
    }

    return session
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

  companion object {
    private val LOG = logger<ZshCompletionTest>()
  }
}