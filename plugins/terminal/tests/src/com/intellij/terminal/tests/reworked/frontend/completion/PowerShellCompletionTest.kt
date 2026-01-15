package com.intellij.terminal.tests.reworked.frontend.completion

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.tests.reworked.frontend.completion.TerminalCompletionFixture.Companion.doWithCompletionFixture
import com.intellij.terminal.tests.reworked.util.TerminalSessionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.util.system.OS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.block.completion.TerminalCommandCompletionShowingMode
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandExecutionListener
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandFinishedEvent
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalOutputStatus
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.awt.event.KeyEvent
import java.io.File
import java.nio.file.Path
import kotlin.coroutines.resume
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.seconds

@RunWith(Parameterized::class)
internal class PowerShellCompletionTest(private val shellPath: Path) : BasePlatformTestCase() {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun shells(): List<Path> {
      return TerminalSessionTestUtil.getPowerShellPaths()
    }
  }

  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `check command names are suggested`() {
    doTest { fixture ->
      fixture.type("pw")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("pwd")
    }
  }

  @Test
  fun `check built-in variables are suggested`() {
    doTest { fixture ->
      fixture.type($$"echo $PS")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("PSVersionTable")
    }
  }

  @Test
  fun `check method names are suggested`() {
    doTest { fixture ->
      fixture.type("""
        "aaa".
      """.trimIndent())
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Equals", "Length", "GetType")
    }
  }

  @Test
  fun `check properties of variables are suggested`() {
    doTest { fixture ->
      fixture.type($$"$host.")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Version", "Name", "UI")
    }
  }

  @Test
  fun `check cmdlet options are suggested`() {
    doTest { fixture ->
      fixture.type("Get-Content -")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Path", "Filter", "Encoding")
    }
  }

  @Test
  fun `check namespace names are suggested`() {
    doTest { fixture ->
      fixture.type("[System")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("System", "SystemPolicy", "SystemEvents")
    }
  }

  @Test
  fun `check type names are suggested`() {
    doTest { fixture ->
      fixture.type("[System.Text.")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Encoder", "Decoder")
    }
  }

  @Test
  fun `check static members are suggested`() {
    doTest { fixture ->
      fixture.type("[Math]::")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Abs", "Sqrt", "PI")
    }
  }

  @Test
  fun `check enum values are suggested`() {
    doTest { fixture ->
      fixture.type("Set-ExecutionPolicy ")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("AllSigned", "Bypass", "Restricted")
    }
  }

  @Test
  fun `check history names are suggested`() {
    doTest { fixture ->
      fixture.executeCommandAndAwaitNextPrompt("ls")
      fixture.executeCommandAndAwaitNextPrompt("pwd")

      fixture.type("#")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .hasSameElementsAs(listOf("ls", "pwd"))
    }
  }

  /**
   * PowerShell adds aliases for popular Unix commands that actually point to PowerShell cmdlets.
   * For example, `cd` is the alias for `Set-Location`.
   * And `ls` is the alias for `Get-ChildItem`.
   *
   * It is important that our completion logic recognizes these aliases
   * to not show unrelated options from Unix command completion specifications.
   */
  @Test
  fun `check PowerShell aliases are recognized`() {
    Assume.assumeTrue(OS.CURRENT == OS.Windows)

    doTest { fixture ->
      fixture.type("ls -")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Name", "Path", "Attributes")   // PowerShell `Get-ChildItem` options
        .doesNotContain("-a", "-l", "a", "l")     // Unix `ls` options
    }
  }

  @Test
  fun `check properties are suggested in pipeline`() {
    doTest { fixture ->
      fixture.type("Get-Process | Select-Object ")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Id", "ProcessName", "CPU")
    }
  }

  @Test
  fun `check options are suggested on the next line after continuation`() {
    doTest { fixture ->
      fixture.type("Get-Content `")
      fixture.pressKey(KeyEvent.VK_ENTER)
      fixture.type("-")

      fixture.assertOutputModelState { model ->
        val text = model.getText(model.startOffset, model.endOffset)
        text.contains(">> -")  // Check that line continuation is present
      }

      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Path", "Filter", "Encoding")
    }
  }

  @Test
  fun `check only files are suggested for absolute path with Windows file separator`() {
    Assume.assumeTrue(OS.CURRENT == OS.Windows)

    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createFile("file.txt")
        it.createDirectory("dir")
        it.createFile(".hidden")
      }

      fixture.type("dir $tempDir\\")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .hasSameElementsAs(listOf("file.txt", "dir\\", ".hidden"))
    }
  }

  @Test
  fun `check only files are suggested for absolute path with Unix file separator`() {
    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createFile("file.txt")
        it.createDirectory("dir")
        it.createFile(".hidden")
      }

      fixture.type("dir $tempDir/")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .hasSameElementsAs(listOf("file.txt", "dir${File.separator}", ".hidden"))
    }
  }

  @Test
  fun `check files are suggested after no prefix`() {
    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createFile("file.txt")
        it.createDirectory("dir")
        it.createFile(".hidden")
      }

      fixture.executeCommandAndAwaitNextPrompt("cd $tempDir")

      fixture.type("dir ")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("file.txt", "dir${File.separator}", ".hidden")
    }
  }

  @Test
  fun `check files are suggested after relative file prefix`() {
    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createFile("file1.txt")
        it.createFile("file2.log")
        it.createDirectory("figures")
        it.createFile(".hidden")
      }

      fixture.executeCommandAndAwaitNextPrompt("cd $tempDir")

      fixture.type("dir fi")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .hasSameElementsAs(listOf("file1.txt", "file2.log", "figures${File.separator}"))
    }
  }

  @Test
  fun `check files are suggested after dot based relative path prefix`() {
    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createFile("file.txt")
        it.createDirectory("dir")
        it.createFile(".hidden")
      }

      fixture.executeCommandAndAwaitNextPrompt("cd $tempDir")

      fixture.type("dir ./")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("file.txt", "dir${File.separator}", ".hidden")
    }
  }

  @Test
  fun `check files are suggested in double quotes`() {
    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createFile("file with spaces.txt")
        it.createFile("abcde.txt")
      }

      fixture.type("dir \"$tempDir/")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("file with spaces.txt", "abcde.txt")
    }
  }

  @Test
  fun `check files are suggested in single quotes`() {
    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createFile("file with spaces.txt")
        it.createFile("abcde.txt")
      }

      fixture.type("dir '$tempDir/")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("file with spaces.txt", "abcde.txt")
    }
  }

  @Test
  fun `check files are suggested when there is no command`() {
    doTest { fixture ->
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
  }

  @Test
  fun `check files are suggested after unknown command`() {
    doTest { fixture ->
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
  }

  private fun doTest(block: suspend (TerminalCompletionFixture) -> Unit) {
    timeoutRunBlocking(timeout = 20.seconds, context = Dispatchers.EDT) {
      val fixtureScope = childScope("TerminalCompletionFixture")

      val session = TerminalSessionTestUtil.startTestTerminalSession(
        project,
        shellPath.toString(),
        System.getProperty("user.home"),
        fixtureScope.childScope("TerminalSession")
      ).session

      doWithCompletionFixture(project, session, fixtureScope) { fixture ->
        fixture.setCompletionOptions(
          showPopupAutomatically = false,
          showingMode = TerminalCommandCompletionShowingMode.ONLY_PARAMETERS,
          parentDisposable = testRootDisposable
        )
        fixture.awaitShellIntegrationFeaturesInitialized()

        block(fixture)
      }
    }
  }

  private suspend fun TerminalCompletionFixture.executeCommandAndAwaitNextPrompt(command: String) {
    val shellIntegration = view.shellIntegrationDeferred.await()

    suspendCancellableCoroutine { continuation ->
      val disposable = Disposer.newDisposable()
      continuation.invokeOnCancellation { Disposer.dispose(disposable) }
      shellIntegration.addCommandExecutionListener(disposable, object : TerminalCommandExecutionListener {
        override fun commandFinished(event: TerminalCommandFinishedEvent) {
          Disposer.dispose(disposable)
          continuation.resume(Unit)
        }
      })

      view.createSendTextBuilder()
        .shouldExecute()
        .send(command)
    }

    shellIntegration.outputStatus.first { it == TerminalOutputStatus.TypingCommand }
  }

  private suspend fun TerminalCompletionFixture.assertOutputModelState(
    condition: (TerminalOutputModel) -> Boolean,
  ) {
    val conditionMet = awaitOutputModelState(3.seconds, condition)

    val model = outputModel
    assertThat(conditionMet)
      .overridingErrorMessage {
        """
        Output model text doesn't match the condition.
        Current text: '${model.getText(model.startOffset, model.endOffset)}', cursor offset: ${model.cursorOffset}
      """.trimIndent()
      }
      .isTrue
  }

  private fun createTempDir(): Path {
    return createTempDirectory().also {
      Disposer.register(testRootDisposable) { it.deleteRecursively() }
    }
  }
}
