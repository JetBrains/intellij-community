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
import com.intellij.util.PathUtil
import com.intellij.util.system.OS
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.block.completion.TerminalCommandCompletionShowingMode
import org.jetbrains.plugins.terminal.session.impl.TerminalCloseEvent
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
  fun `check command names are suggested and inserted correctly`() {
    doTest { fixture ->
      fixture.type("Get-P")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Get-Process", "Get-Package", "Get-PSDrive")

      fixture.insertCompletionItem("Get-Process")
      fixture.assertCommandTextState("Get-Process<cursor>")
    }
  }

  @Test
  fun `check built-in variables are suggested and inserted correctly`() {
    doTest { fixture ->
      fixture.type($$"echo $PS")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("PSVersionTable")

      fixture.insertCompletionItem("PSVersionTable")
      fixture.assertCommandTextState($$"echo $PSVersionTable<cursor>")
    }
  }

  @Test
  fun `check method names are suggested and inserted correctly`() {
    doTest { fixture ->
      fixture.type("""
        "aaa".
      """.trimIndent())
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Equals", "Length", "GetType")

      fixture.insertCompletionItem("Equals")
      fixture.assertCommandTextState("""
        "aaa".Equals(<cursor>
      """.trimIndent())
    }
  }

  @Test
  fun `check properties of variables are suggested and inserted correctly`() {
    doTest { fixture ->
      fixture.type($$"$host.")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Version", "Name", "UI")

      fixture.insertCompletionItem("Version")
      fixture.assertCommandTextState($$"$host.Version<cursor>")
    }
  }

  @Test
  fun `check cmdlet options are suggested and inserted correctly`() {
    doTest { fixture ->
      fixture.type("Get-Content -")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Path", "Filter", "Encoding")

      fixture.insertCompletionItem("Path")
      fixture.assertCommandTextState("Get-Content -Path<cursor>")
    }
  }

  @Test
  fun `check namespace names are suggested and inserted correctly`() {
    doTest { fixture ->
      fixture.type("[System")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("System", "SystemEvents")

      fixture.insertCompletionItem("SystemEvents")
      fixture.assertCommandTextState("[Microsoft.Win32.SystemEvents<cursor>")
    }
  }

  @Test
  fun `check type names are suggested and inserted correctly`() {
    doTest { fixture ->
      fixture.type("[System.Text.")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Encoder", "Decoder")

      fixture.insertCompletionItem("Encoder")
      fixture.assertCommandTextState("[System.Text.Encoder<cursor>")
    }
  }

  @Test
  fun `check static members are suggested and inserted correctly`() {
    doTest { fixture ->
      fixture.type("[Math]::")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Abs", "Sqrt", "PI")

      fixture.insertCompletionItem("Abs")
      fixture.assertCommandTextState("[Math]::Abs(<cursor>")
    }
  }

  @Test
  fun `check enum values are suggested and inserted correctly`() {
    doTest { fixture ->
      fixture.type("Set-ExecutionPolicy ")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("AllSigned", "Bypass", "Restricted")

      fixture.insertCompletionItem("AllSigned")
      fixture.assertCommandTextState("Set-ExecutionPolicy AllSigned<cursor>")
    }
  }

  @Test
  fun `check history names are suggested and inserted correctly`() {
    doTest { fixture ->
      fixture.executeCommandAndAwaitNextPrompt("ls")
      fixture.executeCommandAndAwaitNextPrompt("pwd")

      fixture.type("#")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .hasSameElementsAs(listOf("ls", "pwd"))

      fixture.insertCompletionItem("pwd")
      fixture.assertCommandTextState("pwd<cursor>")
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
  fun `check properties are suggested in pipeline and inserted correctly`() {
    doTest { fixture ->
      fixture.type("Get-Process | Select-Object ")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Id", "ProcessName", "CPU")

      fixture.insertCompletionItem("ProcessName")
      fixture.assertCommandTextState("Get-Process | Select-Object ProcessName<cursor>")
    }
  }

  @Test
  fun `check options are suggested on the next line after continuation and inserted correctly`() {
    doTest { fixture ->
      fixture.type("Get-Content `")
      fixture.pressKey(KeyEvent.VK_ENTER)
      fixture.type("-")

      fixture.awaitOutputModelState(3.seconds) { model ->
        val text = model.getText(model.startOffset, model.endOffset)
        text.contains(">> -")  // Check that line continuation is present
      }

      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("Path", "Filter", "Encoding")

      fixture.insertCompletionItem("Path")
      fixture.assertCommandTextState("Get-Content `\n>> -Path<cursor>")
    }
  }

  @Test
  fun `check files are suggested for absolute path with Windows file separator and inserted correctly`() {
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

      fixture.insertCompletionItem("file.txt")
      fixture.assertCommandTextState("dir $tempDir\\file.txt<cursor>")
    }
  }

  @Test
  fun `check files are suggested for absolute path with Unix file separator and inserted correctly`() {
    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createFile("file.txt")
        it.createDirectory("dir")
        it.createFile(".hidden")
      }

      fixture.type("dir $tempDir/")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .hasSameElementsAs(listOf("file.txt", "dir$separator", ".hidden"))

      fixture.insertCompletionItem("dir$separator")
      fixture.assertCommandTextState("dir $tempDir${separator}dir${separator}<cursor>")
    }
  }

  @Test
  fun `check files are suggested after no prefix and inserted correctly`() {
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
        .contains("file.txt", "dir${separator}", ".hidden")

      fixture.insertCompletionItem("file.txt")
      fixture.assertCommandTextState("dir .${separator}file.txt<cursor>")
    }
  }

  @Test
  fun `check files are suggested after relative file prefix and inserted correctly`() {
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
        .hasSameElementsAs(listOf("file1.txt", "file2.log", "figures$separator"))

      fixture.insertCompletionItem("file1.txt")
      fixture.assertCommandTextState("dir .${separator}file1.txt<cursor>")
    }
  }

  @Test
  fun `check files are suggested after dot based relative path prefix and inserted correctly`() {
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
        .contains("file.txt", "dir$separator", ".hidden")

      fixture.insertCompletionItem("dir$separator")
      fixture.assertCommandTextState("dir .${separator}dir${separator}<cursor>")
    }
  }

  @Test
  fun `check files are suggested after double quote and directory inserted correctly`() {
    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createFile("file with spaces.txt")
        it.createFile("abcde.txt")
        it.createDirectory("dir")
      }

      fixture.type("dir \"$tempDir/")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("file with spaces.txt", "abcde.txt", "dir$separator")

      fixture.insertCompletionItem("dir$separator")
      fixture.assertCommandTextState("dir \"$tempDir${separator}dir${separator}<cursor>\"")
    }
  }

  @Test
  fun `check files are suggested after single quote and directory inserted correctly`() {
    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createFile("file with spaces.txt")
        it.createFile("abcde.txt")
        it.createDirectory("dir")
      }

      fixture.type("dir '$tempDir/")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("file with spaces.txt", "abcde.txt", "dir$separator")

      fixture.insertCompletionItem("dir$separator")
      fixture.assertCommandTextState("dir '$tempDir${separator}dir${separator}<cursor>'")
    }
  }

  @Test
  fun `check files are suggested inside double quotes and directory inserted correctly`() {
    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createFile("file with spaces.txt")
        it.createFile("abcde.txt")
        it.createDirectory("dir")
      }

      fixture.type("""
        dir "$tempDir/"
      """.trimIndent())
      fixture.pressKey(KeyEvent.VK_LEFT)
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("file with spaces.txt", "abcde.txt", "dir$separator")

      fixture.insertCompletionItem("dir$separator")
      fixture.assertCommandTextState("dir \"$tempDir${separator}dir${separator}<cursor>\"")
    }
  }

  @Test
  fun `check files are suggested inside single quotes and directory inserted correctly`() {
    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createFile("file with spaces.txt")
        it.createFile("abcde.txt")
        it.createDirectory("dir")
      }

      fixture.type("""
        dir '$tempDir/'
      """.trimIndent())
      fixture.pressKey(KeyEvent.VK_LEFT)
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("file with spaces.txt", "abcde.txt", "dir$separator")

      fixture.insertCompletionItem("dir$separator")
      fixture.assertCommandTextState("dir '$tempDir${separator}dir${separator}<cursor>'")
    }
  }

  @Test
  fun `check files are suggested inside double quotes and file name is inserted correctly`() {
    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createFile("file with spaces.txt")
        it.createFile("abcde.txt")
        it.createDirectory("dir")
      }

      fixture.type("""
        dir "$tempDir/"
      """.trimIndent())
      fixture.pressKey(KeyEvent.VK_LEFT)
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("file with spaces.txt", "abcde.txt", "dir$separator")

      fixture.insertCompletionItem("abcde.txt")
      fixture.assertCommandTextState("dir \"$tempDir${separator}abcde.txt<cursor>\"")
    }
  }

  @Test
  fun `check files are suggested inside single quotes and file name is inserted correctly`() {
    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createFile("file with spaces.txt")
        it.createFile("abcde.txt")
        it.createDirectory("dir")
      }

      fixture.type("""
        dir '$tempDir/'
      """.trimIndent())
      fixture.pressKey(KeyEvent.VK_LEFT)
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("file with spaces.txt", "abcde.txt", "dir$separator")

      fixture.insertCompletionItem("dir$separator")
      fixture.assertCommandTextState("dir '$tempDir${separator}dir${separator}<cursor>'")
    }
  }

  @Test
  fun `check file with spaces is surrounded with single quotes on insertion`() {
    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createFile("file with spaces.txt")
        it.createFile("abcde.txt")
        it.createDirectory("dir")
      }

      fixture.type("dir $tempDir/")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .contains("file with spaces.txt", "abcde.txt", "dir$separator")

      fixture.insertCompletionItem("file with spaces.txt")
      fixture.assertCommandTextState("dir '$tempDir${separator}file with spaces.txt<cursor>'")
    }
  }

  @Test
  fun `check files are suggested when there is no command and inserted correctly`() {
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
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .hasSameElementsAs(listOf("file.txt", "figures$separator", "files$separator"))

      fixture.insertCompletionItem("file.txt")
      fixture.assertCommandTextState("$tempDir${separator}file.txt<cursor>")
    }
  }

  @Test
  fun `check files are suggested when there is no command and file path with spaces is inserted correctly`() {
    doTest { fixture ->
      val tempDir = createTempDir().also {
        it.createDirectory("dir with spaces")
        it.createDirectory("director")
        it.createFile("direction.txt")
      }

      fixture.type("$tempDir/di")
      fixture.callCompletionPopup()
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .hasSameElementsAs(listOf("dir with spaces$separator", "director$separator", "direction.txt"))

      fixture.insertCompletionItem("dir with spaces$separator")
      fixture.assertCommandTextState("& '$tempDir${separator}dir with spaces${separator}<cursor>'")
    }
  }

  @Test
  fun `check files are suggested after unknown command and inserted correctly`() {
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
      assertThat(fixture.getLookupElements().map { it.lookupString })
        .hasSameElementsAs(listOf("file.txt", "figures$separator", "files$separator"))

      fixture.insertCompletionItem("files$separator")
      fixture.assertCommandTextState("some_unknown_command $tempDir${separator}files${separator}<cursor>")
    }
  }

  private fun doTest(block: suspend (TerminalCompletionFixture) -> Unit) {
    timeoutRunBlocking(timeout = 20.seconds, context = Dispatchers.EDT) {
      // Disable PowerShell 7 Inline Completion (gray text)
      val executableName = PathUtil.getFileName(shellPath.toString())
      val envVariables = if (executableName == "pwsh.exe" || executableName == "pwsh") {
        mapOf("JEDITERM_SOURCE" to "Set-PSReadLineOption -PredictionSource None")
      }
      else emptyMap()

      val startupOptions = ShellStartupOptions.Builder()
        .shellCommand(listOf(shellPath.toString()))
        .workingDirectory(System.getProperty("user.home"))
        .envVariables(envVariables)
        // Use the wide terminal size because ConPTY may insert sudden line breaks when inserting a completion item.
        .initialTermSize(TermSize(300, 30))
        .build()

      val sessionScope = childScope("TerminalSession")
      val session = TerminalSessionTestUtil.startTestTerminalSession(
        project,
        startupOptions,
        isLowLevelSession = false,
        coroutineScope = sessionScope
      ).session

      val fixtureScope = childScope("TerminalCompletionFixture")
      try {
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
      finally {
        // Session scope should be terminated in a result of sending the close event.
        session.getInputChannel().send(TerminalCloseEvent())
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

  private fun createTempDir(): Path {
    return createTempDirectory().toRealPath().also {
      Disposer.register(testRootDisposable) { it.deleteRecursively() }
    }
  }

  private val separator = File.separator
}
