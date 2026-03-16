package com.intellij.terminal.tests.reworked.frontend.completion

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.tests.reworked.frontend.completion.TerminalCompletionFixture.Companion.doWithCompletionFixture
import com.intellij.terminal.tests.reworked.frontend.completion.TerminalCompletionPopupTest.Companion.MAX_ITEMS_COUNT
import com.intellij.terminal.tests.reworked.util.EchoingTerminalSession
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import com.intellij.testFramework.utils.io.deleteRecursively
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.completion.TerminalCommandCompletionShowingMode
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCommandSpec
import org.jetbrains.plugins.terminal.block.completion.spec.ShellCompletionSuggestion
import org.jetbrains.plugins.terminal.block.reworked.TerminalCommandCompletion
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.session.impl.TerminalStartupOptionsImpl
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.event.KeyEvent.VK_BACK_SPACE
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_RIGHT
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

@RunWith(JUnit4::class)
class TerminalCompletionPopupTest : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean = false

  val testCommandSpec = ShellCommandSpec("test_cmd") {
    subcommands {
      subcommand("status")
      subcommand("stop")
      subcommand("set")
      subcommand("sync")
      subcommand("show") {
        argument {
          optional()
          suggestions("roots", "files", "statuses")
        }
      }

      subcommand("start") {
        argument {
          optional()
          suggestions("platform/", "platform-ui/", "shared\\", "shared-ui\\")
        }
      }
    }

    subcommands {
      subcommand("bind")
      subcommand("branch")
      subcommand("build") {
        argument {
          suggestions {
            val count = MAX_ITEMS_COUNT + 10
            val items = buildList {
              repeat(count) {
                add("ab$it")
                add("ac$it")
              }
            }

            val abPriority = ShellCompletionSuggestion("ab") { priority(100) }
            val acPriority = ShellCompletionSuggestion("ac") { priority(100) }
            listOf(abPriority, acPriority) + items.map { ShellCompletionSuggestion(it) }
          }
        }
      }
    }
  }

  @Test
  fun `test completions list filtered on typing`() = doTest { fixture ->
    fixture.type("test_cmd ")
    fixture.callCompletionPopup()
    assertEquals(true, fixture.isLookupActive())
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("bind", "branch", "build", "set", "show", "start", "status", "stop", "sync"))

    fixture.type("s")
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("set", "show", "start", "status", "stop", "sync"))

    fixture.type("t")
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("start", "status", "stop"))
  }

  @Test
  fun `test completion popup shows for command starting with space`() = doTest { fixture ->
    fixture.type(" test_cmd ")
    fixture.callCompletionPopup()
    assertEquals(true, fixture.isLookupActive())
  }

  @Test
  fun `test selection returns to original item after down and up actions`() = doTest { fixture ->
    fixture.type("test_cmd ")
    fixture.callCompletionPopup()
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("bind", "branch", "build", "set", "show", "start", "status", "stop", "sync"))

    val firstElement = fixture.getCurrentItem()
    fixture.downCompletionPopup()
    val secondElement = fixture.getCurrentItem()
    fixture.upCompletionPopup()
    val firstAfterElement = fixture.getCurrentItem()

    assertEquals(firstElement, firstAfterElement)
    assertNotEquals(secondElement, firstAfterElement)
  }

  @Test
  fun `test completion list is re-filtered when caret moves over prefix to the left, right`() = doTest { fixture ->
    fixture.type("test_cmd ")
    fixture.callCompletionPopup()

    fixture.type("sta")
    val startResult = fixture.getLookupElements()
    assertSameElements(startResult.map { it.lookupString }, listOf("start", "status"))

    fixture.pressKey(VK_LEFT)
    val afterFirstLeftResult = fixture.getLookupElements()
    assertSameElements(afterFirstLeftResult.map { it.lookupString },
                       listOf("start", "status", "stop"))

    fixture.pressKey(VK_LEFT)
    val afterSecondLeftResult = fixture.getLookupElements()
    assertSameElements(afterSecondLeftResult.map { it.lookupString },
                       listOf("set", "show", "start", "status", "stop", "sync"))

    fixture.pressKey(VK_RIGHT)
    val afterRightResult = fixture.getLookupElements()
    assertEquals(afterFirstLeftResult, afterRightResult)

    fixture.pressKey(VK_RIGHT)
    val afterSecondRightResult = fixture.getLookupElements()
    assertEquals(startResult, afterSecondRightResult)
  }

  @Test
  fun `test lookup remains active when caret moves into text typed before call popup`() = doTest { fixture ->
    fixture.type("test_cmd st")
    fixture.callCompletionPopup()

    val startResult = fixture.getLookupElements().map { it.lookupString }
    assertSameElements(startResult, listOf("start", "status", "stop"))
    fixture.pressKey(VK_LEFT)
    fixture.awaitLookupElementsEqual("set", "show", "start", "status", "stop", "sync")

    fixture.pressKey(VK_RIGHT)
    assertSameElements(fixture.getLookupElements().map { it.lookupString }, startResult)
  }

  @Test
  fun `test completion list is correctly re-filtered after pressing backspace`() = doTest { fixture ->
    fixture.type("test_cmd st")
    fixture.callCompletionPopup()

    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("start", "status", "stop"))

    fixture.type("a")
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("start", "status"))

    fixture.pressKey(VK_BACK_SPACE)
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("start", "status", "stop"))

    fixture.pressKey(VK_BACK_SPACE)
    fixture.awaitLookupElementsEqual("set", "show", "start", "status", "stop", "sync")
  }

  @Test
  fun `test completion popup is reopened on any prefix change when suggestions count reach the limit`() = doTest { fixture ->
    fixture.type("test_cmd build a")
    fixture.callCompletionPopup()
    fixture.awaitLookupElementsSatisfy { items ->
      items.size == MAX_ITEMS_COUNT_AFTER_TRIM && !items.all { it.startsWith("ab") }
    }

    // Popup should reopen with items for the "ab" prefix
    fixture.type("b")
    fixture.awaitLookupElementsSatisfy { items ->
      items.size == MAX_ITEMS_COUNT_AFTER_TRIM && items.all { it.startsWith("ab") }
    }

    // Popup should reopen with items for the "a" prefix
    fixture.pressKey(VK_LEFT)
    fixture.awaitLookupElementsSatisfy { items ->
      items.size == MAX_ITEMS_COUNT_AFTER_TRIM && !items.all { it.startsWith("ab") }
    }

    // Popup should reopen with items for the "ab" prefix
    fixture.pressKey(VK_RIGHT)
    fixture.awaitLookupElementsSatisfy { items ->
      items.size == MAX_ITEMS_COUNT_AFTER_TRIM && items.all { it.startsWith("ab") }
    }

    // Popup should reopen with items for the "a" prefix
    fixture.pressKey(VK_BACK_SPACE)
    fixture.awaitLookupElementsSatisfy { items ->
      items.size == MAX_ITEMS_COUNT_AFTER_TRIM && !items.all { it.startsWith("ab") }
    }

    // Popup should reopen with items for the "ac" prefix
    fixture.type("c")
    fixture.awaitLookupElementsSatisfy { items ->
      items.size == MAX_ITEMS_COUNT_AFTER_TRIM && items.all { it.startsWith("ac") }
    }
  }

  @Test
  fun `test completion popup closes on empty prefix after pressing backspace`() = doTest { fixture ->
    fixture.type("test_cmd st")
    fixture.callCompletionPopup()

    fixture.pressKey(VK_BACK_SPACE)
    fixture.awaitLookupElementsEqual("set", "show", "start", "status", "stop", "sync")

    fixture.pressKey(VK_BACK_SPACE)
    fixture.awaitPendingRequestsProcessed()
    assertFalse(fixture.isLookupActive())
  }

  @Test
  fun `test completion popup closes on empty prefix after pressing left`() = doTest { fixture ->
    fixture.type("test_cmd st")
    fixture.callCompletionPopup()

    fixture.pressKey(VK_LEFT)
    fixture.awaitLookupElementsEqual("set", "show", "start", "status", "stop", "sync")

    fixture.pressKey(VK_LEFT)
    fixture.awaitPendingRequestsProcessed()
    assertFalse(fixture.isLookupActive())
  }

  @Test
  fun `test completion popup closes on pressing left when completion called on empty prefix`() = doTest { fixture ->
    fixture.type("test_cmd show ")
    fixture.callCompletionPopup()
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("roots", "files", "statuses"))

    fixture.pressKey(VK_LEFT)
    fixture.awaitPendingRequestsProcessed()
    assertFalse(fixture.isLookupActive())
  }

  @Test
  fun `test completion popup closes on non-matching prefix`() = doTest { fixture ->
    fixture.type("test_cmd st")
    fixture.callCompletionPopup()
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("start", "status", "stop"))

    fixture.type("x")
    assertFalse(fixture.isLookupActive())
  }

  @Test
  fun `test completion popup closes when single item is fully typed`() = doTest { fixture ->
    fixture.type("test_cmd st")
    fixture.callCompletionPopup()
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("start", "status", "stop"))

    fixture.type("art")
    assertFalse(fixture.isLookupActive())
  }

  @Test
  fun `test completion popup closes when any text appears below the line with cursor`() = doTest { fixture ->
    fixture.type("test_cmd st")
    fixture.callCompletionPopup()
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("start", "status", "stop"))

    fixture.outputModel.lookupAwareUpdate(1, "some text")
    fixture.awaitPendingRequestsProcessed()
    assertFalse(fixture.isLookupActive())
  }

  @Test
  fun `test completion popup closes when whole prefix is replaced in a single update`() = doTest { fixture ->
    fixture.type("test_cmd ST")
    fixture.callCompletionPopup()
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("start", "status", "stop"))

    fixture.outputModel.lookupAwareUpdate(0, "test_cmd start")
    fixture.awaitPendingRequestsProcessed()
    assertFalse(fixture.isLookupActive())
  }

  @Test
  fun `test exact match for directory item is placed first (Unix separator)`() = doTest { fixture ->
    fixture.type("test_cmd start plat")
    fixture.callCompletionPopup()
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("platform/", "platform-ui/"))
    fixture.type("form")

    val firstElement = fixture.getCurrentItem()
    assertEquals("platform/", firstElement?.lookupString)
  }

  @Test
  fun `test exact match for directory item is placed first (Windows separator)`() = doTest { fixture ->
    fixture.type("test_cmd start sha")
    fixture.callCompletionPopup()
    assertSameElements(fixture.getLookupElements().map { it.lookupString },
                       listOf("shared\\", "shared-ui\\"))
    fixture.type("red")

    val firstElement = fixture.getCurrentItem()
    assertEquals("shared\\", firstElement?.lookupString)
  }

  @Test
  fun `test terminal LookupElement#object is ShellCompletionSuggestion`() = doTest { fixture ->
    fixture.type("test_cmd st")
    fixture.callCompletionPopup()
    val elements = fixture.getLookupElements()
    assertThat(elements)
      .isNotEmpty
      .allMatch { it.`object` is ShellCompletionSuggestion }
  }

  @Test
  fun `test TerminalCommandCompletion#LAST_SELECTED_ITEM_KEY is updated in the lookup on selected item change`() = doTest { fixture ->
    fixture.type("test_cmd st")
    fixture.callCompletionPopup()

    // Check that the initial selected item value is set
    val lookup = fixture.getActiveLookup() ?: error("No active lookup")
    assertThat(lookup.items.map { it.lookupString })
      .hasSameElementsAs(listOf("start", "status", "stop"))
    val firstSelectedItem = lookup.getUserData(TerminalCommandCompletion.LAST_SELECTED_ITEM_KEY)
    assertThat(firstSelectedItem).isNotNull

    // Narrow down the prefix and check that the selected item value is updated
    fixture.type("o")
    assertThat(lookup.items.map { it.lookupString })
      .hasSameElementsAs(listOf("stop"))
    val secondSelectedItem = lookup.getUserData(TerminalCommandCompletion.LAST_SELECTED_ITEM_KEY)
    assertThat(secondSelectedItem)
      .isNotNull
      .matches { it?.lookupString == "stop" }
      .isNotEqualTo(firstSelectedItem)

    // Type an unrelated prefix to close the popup and check that the last selected item stay the same.
    fixture.type(" ")
    assertThat(lookup.isLookupDisposed).isTrue
    val lastSelectedItem = lookup.getUserData(TerminalCommandCompletion.LAST_SELECTED_ITEM_KEY)
    assertThat(lastSelectedItem)
      .isNotNull
      .isEqualTo(secondSelectedItem)
  }

  @Test
  fun `test file names are suggested as a fallback when there is no command`() = doTest { fixture ->
    val tempDir = createTempDir().also {
      it.createFile("file.txt")
      it.createDirectory("figures")
      it.createDirectory("files")
      it.createDirectory("dir")
      it.createFile(".hidden")
    }

    fixture.type("$tempDir/fi")
    fixture.callCompletionPopup()
    val separator = File.separator
    assertThat(fixture.getLookupElements().map { it.lookupString })
      .hasSameElementsAs(listOf("file.txt", "figures$separator", "files$separator"))
  }

  @Test
  fun `test file names are suggested as a fallback after unknown command`() = doTest { fixture ->
    val tempDir = createTempDir().also {
      it.createFile("file.txt")
      it.createDirectory("figures")
      it.createDirectory("files")
      it.createDirectory("dir")
      it.createFile(".hidden")
    }

    fixture.type("some_unknown_command $tempDir/fi")
    fixture.callCompletionPopup()
    val separator = File.separator
    assertThat(fixture.getLookupElements().map { it.lookupString })
      .hasSameElementsAs(listOf("file.txt", "figures$separator", "files$separator"))
  }

  private fun doTest(block: suspend (TerminalCompletionFixture) -> Unit) = timeoutRunBlocking(context = Dispatchers.EDT) {
    val fixtureScope = childScope("TerminalCompletionFixture")
    val startupOptions = TerminalStartupOptionsImpl(
      shellCommand = listOf("/bin/zsh", "--login", "-i"),
      workingDirectory = "fakeDir",
      envVariables = emptyMap(),
      pid = null,
    )
    val session = EchoingTerminalSession(startupOptions, fixtureScope.childScope("EchoingTerminalSession"))
    doWithCompletionFixture(project, session, fixtureScope) { fixture ->
      fixture.mockTestShellCommand(testCommandSpec)
      fixture.setCompletionOptions(
        showPopupAutomatically = false,
        showingMode = TerminalCommandCompletionShowingMode.ONLY_PARAMETERS,
        parentDisposable = testRootDisposable
      )
      fixture.awaitShellIntegrationFeaturesInitialized()

      block(fixture)
    }
  }

  private fun createTempDir(): Path {
    return createTempDirectory().also {
      Disposer.register(testRootDisposable) { it.deleteRecursively() }
    }
  }

  private fun MutableTerminalOutputModel.lookupAwareUpdate(absoluteLineIndex: Long, text: String) {
    val doUpdate = {
      this.updateContent(absoluteLineIndex, text, emptyList())
      this.updateCursorPosition(absoluteLineIndex, text.length)
    }
    val lookup = LookupManager.getInstance(project).activeLookup
    if (lookup != null && lookup.editor.isReworkedTerminalEditor) {
      lookup.performGuardedChange(doUpdate)
    }
    else {
      doUpdate()
    }
  }

  companion object {
    /** Max items count to be shown in lookup */
    private val MAX_ITEMS_COUNT = Registry.intValue("ide.completion.variant.limit")

    /** Once [MAX_ITEMS_COUNT] limit is reached, lookup trims half of the items and this value becomes true max size */
    private val MAX_ITEMS_COUNT_AFTER_TRIM = MAX_ITEMS_COUNT / 2
  }
}