// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// it is a superclass with common tests, so it is OK to have functions with @Test annotation
@file:Suppress("JUnitMixedFramework")

package org.jetbrains.plugins.terminal.exp

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import com.jediterm.core.util.TermSize
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.exp.util.TerminalSessionTestUtil
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

abstract class BaseShCompletionTest : BasePlatformTestCase() {
  protected abstract val shellPath: String

  protected lateinit var session: TerminalSession
  protected lateinit var testDirectory: Path

  protected abstract fun createCompletionManager(session: TerminalSession): TerminalCompletionManager

  override fun setUp() {
    Assume.assumeTrue("Shell is not found in '$shellPath'", File(shellPath).exists())
    super.setUp()

    val descriptor = DefaultPluginDescriptor("org.jetbrains.plugins.terminal")
    val contributor = CompletionContributorEP("Shell Script", TerminalSessionCompletionContributor::class.java.name, descriptor)
    ExtensionTestUtil.maskExtensions(CompletionContributor.EP, listOf(contributor), testRootDisposable)

    Registry.get(LocalTerminalDirectRunner.BLOCK_TERMINAL_REGISTRY).setValue(true, testRootDisposable)
    session = TerminalSessionTestUtil.startTerminalSession(project, shellPath, testRootDisposable)
    val completionManager = createCompletionManager(session)
    val model = session.model

    myFixture.configureByText("test.sh", "")
    myFixture.editor.putUserData(TerminalSession.KEY, session)
    myFixture.editor.putUserData(TerminalCompletionManager.KEY, completionManager)

    testDirectory = createTempDirectory(prefix = "sh_completion")

    LOG.info("Temp directory path: $testDirectory")
    LOG.info("Initial terminal state:\n${model.withContentLock { model.getAllText() }}")
  }

  override fun tearDown() {
    if (!this::session.isInitialized) {
      return // no shell installed
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

    assertSingleItemCompleted(elements, "acde/")
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

    assertSingleItemCompleted(elements, "abc\\ de/")
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

  protected fun typeLsAndComplete(prefix: String): Array<LookupElement>? {
    myFixture.type("ls ${testDirectory}/$prefix")
    return myFixture.completeBasic()
  }

  protected fun assertPromptRestored() {
    val model = session.model
    if (model.commandExecutionSemaphore.waitFor(5000)) {
      val text = model.withContentLock { model.getAllText() }
      assertTrue("Prompt is not restored: '$text'", text.isEmpty())
    }
    else {
      fail("Failed to acquire command execution lock, seems that prompt is broken, text buffer:\n" +
           model.withContentLock { model.getAllText() })
    }
  }

  protected fun assertSingleItemCompleted(elements: Array<LookupElement>?, expectedItem: String) {
    if (!elements.isNullOrEmpty()) {
      assertSameElements(elements.map { it.lookupString }, listOf(expectedItem))
    }
    else {
      val promptText = myFixture.editor.document.text
      assertTrue("Incorrect prompt text: '$promptText'", promptText.endsWith(expectedItem))
    }
  }

  companion object {
    private val LOG = logger<BaseShCompletionTest>()
  }
}