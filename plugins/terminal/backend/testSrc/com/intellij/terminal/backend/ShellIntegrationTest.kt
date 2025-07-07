// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend

import com.google.common.base.Ascii
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.backend.util.TerminalSessionTestUtil
import com.intellij.terminal.backend.util.TerminalSessionTestUtil.ENTER_BYTES
import com.intellij.terminal.backend.util.TerminalSessionTestUtil.awaitOutputEvent
import com.intellij.terminal.session.*
import com.intellij.terminal.session.dto.toState
import com.intellij.terminal.session.dto.toStyleRange
import com.intellij.testFramework.*
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jediterm.core.util.TermSize
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.reworked.util.TerminalTestUtil
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

@RunWith(Parameterized::class)
internal class ShellIntegrationTest(private val shellPath: Path) {
  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain: RuleChain = RuleChain(projectRule, disposableRule, DisposeNonLightProjectsRule())

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun shells(): List<Path> {
      return TerminalSessionTestUtil.getShellPaths()
    }
  }

  @Test
  fun `shell integration send correct events on command invocation`() = timeoutRunBlocking(30.seconds) {
    val cwd = System.getProperty("user.home")
    val options = ShellStartupOptions.Builder().workingDirectory(cwd).build()
    val events = startSessionAndCollectOutputEvents(options, isLowLevelSession = true) { input ->
      input.send(TerminalWriteBytesEvent("pwd".toByteArray() + ENTER_BYTES))
    }

    val shellIntegrationEvents = events.filter { it is TerminalShellIntegrationEvent }
    val expectedEvents = listOf(
      TerminalAliasesReceivedEvent::class,
      TerminalPromptStartedEvent::class,
      TerminalPromptFinishedEvent::class,
      TerminalCommandStartedEvent::class,
      TerminalCommandFinishedEvent::class,
      TerminalPromptStartedEvent::class,
      TerminalPromptFinishedEvent::class,
    )

    assertSameEvents(shellIntegrationEvents, expectedEvents, events)
  }

  @Test
  fun `shell integration should not send command finished event without command started event on Ctrl+C`() = timeoutRunBlocking(30.seconds) {
    val events = startSessionAndCollectOutputEvents(isLowLevelSession = true) { input ->
      input.send(TerminalWriteBytesEvent("abcdef".toByteArray()))
      delay(1000)
      input.send(TerminalWriteBytesEvent(CTRL_C_BYTES))
    }

    val shellIntegrationEvents = events.filter { it is TerminalShellIntegrationEvent }
    val expectedEvents = listOf(
      TerminalAliasesReceivedEvent::class,
      TerminalPromptStartedEvent::class,
      TerminalPromptFinishedEvent::class,
      TerminalPromptStartedEvent::class,
      TerminalPromptFinishedEvent::class
    )

    assertSameEvents(shellIntegrationEvents, expectedEvents, events)
  }

  /**
   * This case is specific only for Zsh.
   * By default, in Zsh prompt is not redrawn after showing the completion results.
   * But it is redrawn if completion results occupy the whole screen.
   * It is the exact case we are testing there.
   */
  @Test
  fun `prompt events received after prompt is redrawn because of long completion output`() = timeoutRunBlocking(30.seconds) {
    Assume.assumeTrue(shellPath.toString().contains("zsh"))

    val options = ShellStartupOptions.Builder().initialTermSize(TermSize(80, 4)).build()
    val events = startSessionAndCollectOutputEvents(options, isLowLevelSession = true) { input ->
      input.send(TerminalWriteBytesEvent("g".toByteArray() + TAB_BYTES))
      // Shell can ask "do you wish to see all N possibilities? (y/n)"
      // Wait for this question and ask `y`
      delay(1000)
      input.send(TerminalWriteBytesEvent("y".toByteArray()))
    }

    val shellIntegrationEvents = events.filter { it is TerminalShellIntegrationEvent }
    val expectedEvents = listOf(
      TerminalAliasesReceivedEvent::class,
      TerminalPromptStartedEvent::class,
      TerminalPromptFinishedEvent::class,
      TerminalPromptStartedEvent::class,
      TerminalPromptFinishedEvent::class
    )

    assertSameEvents(shellIntegrationEvents, expectedEvents, events)
  }

  /**
   * This case is specific only for Bash.
   * Be default, in Bash prompt is redrawn after showing of completion items.
   * So, we are testing this case here.
   */
  @Test
  fun `prompt events received after prompt is redrawn because of showing completion items`() = timeoutRunBlocking(30.seconds) {
    Assume.assumeTrue(shellPath.toString().contains("bash"))

    val bindCommand = "bind 'set show-all-if-ambiguous on'"
    val cwd = System.getProperty("user.home")

    val options = ShellStartupOptions.Builder()
      .initialTermSize(TermSize(80, 100))
      .workingDirectory(cwd)
      .build()
    val events = startSessionAndCollectOutputEvents(options, isLowLevelSession = true) { input ->
      // Configure the shell to show completion items on the first Tab key press.
      input.send(TerminalWriteBytesEvent(bindCommand.toByteArray() + ENTER_BYTES))
      delay(1000)
      input.send(TerminalWriteBytesEvent("gi".toByteArray() + TAB_BYTES))
    }

    val shellIntegrationEvents = events.filter { it is TerminalShellIntegrationEvent }
    val expectedEvents = listOf(
      TerminalAliasesReceivedEvent::class,
      // Initialization
      TerminalPromptStartedEvent::class,
      TerminalPromptFinishedEvent::class,
      // Bind command execution
      TerminalCommandStartedEvent::class,
      TerminalCommandFinishedEvent::class,
      TerminalPromptStartedEvent::class,
      TerminalPromptFinishedEvent::class,
      // Prompt redraw after completion
      TerminalPromptStartedEvent::class,
      TerminalPromptFinishedEvent::class
    )

    assertSameEvents(shellIntegrationEvents, expectedEvents, events)
  }

  @Test
  fun `prompt events received after prompt is redrawn because of Ctrl+L`() = timeoutRunBlocking(30.seconds) {
    val events = startSessionAndCollectOutputEvents(isLowLevelSession = true) { input ->
      input.send(TerminalWriteBytesEvent("abcdef".toByteArray()))
      input.send(TerminalWriteBytesEvent(CTRL_L_BYTES))
    }

    val shellIntegrationEvents = events.filter { it is TerminalShellIntegrationEvent }
    val expectedEvents = listOf(
      TerminalAliasesReceivedEvent::class,
      TerminalPromptStartedEvent::class,
      TerminalPromptFinishedEvent::class,
      TerminalPromptStartedEvent::class,
      TerminalPromptFinishedEvent::class
    )

    assertSameEvents(shellIntegrationEvents, expectedEvents, events)
  }

  @Test
  fun `non-zero exit code is received if command has failed`() = timeoutRunBlocking(30.seconds) {
    val events = startSessionAndCollectOutputEvents { input ->
      input.send(TerminalWriteBytesEvent("abracadabra".toByteArray() + ENTER_BYTES))
      delay(2000)
    }

    val commandFinishedEvent = events.find { it is TerminalCommandFinishedEvent }
    assertThat(commandFinishedEvent)
      .overridingErrorMessage { "Failed to find command finished event.\n${dumpTerminalState(events)}" }
      .isNotNull
    assertThat((commandFinishedEvent as TerminalCommandFinishedEvent).exitCode)
      .overridingErrorMessage { "Expected exit code to be non-zero.\n${dumpTerminalState(events)}" }
      .isNotEqualTo(0)
    Unit
  }

  @Test
  fun `zsh integration can change PS1`() = timeoutRunBlocking(30.seconds) {
    Assume.assumeTrue(shellPath.name == "zsh")

    val enforcedPS1 = "my-enforced-PS1>"
    val zdotdir = Files.createTempDirectory("zsh-custom-zdotdir")
    zdotdir.resolve(".zshrc").writeText("""
      # Overwrite PS1, like PowerLevel10k does
      PS1="$enforcedPS1"
      builtin autoload -Uz add-zsh-hook

      function enforcePS1() {
        PS1="$enforcedPS1"
        # re-add `enforcePS1` to ensure it runs last
        add-zsh-hook -d precmd enforcePS1
        add-zsh-hook precmd enforcePS1
      }

      add-zsh-hook precmd enforcePS1
    """.trimIndent())

    val envs = mapOf("ZDOTDIR" to zdotdir.toString())
    val options = ShellStartupOptions.Builder().envVariables(envs).build()

    val events = startSessionAndCollectOutputEvents(options) {}
    val promptFinishedEvent = events.find { it is TerminalPromptFinishedEvent }
    assertThat(promptFinishedEvent)
      .overridingErrorMessage { "Failed to find TerminalPromptFinishedEvent. All events:\n${events.map { it::class.java.simpleName }}" }
      .isNotNull
    Unit
  }

  @Test
  fun `zsh integration should be executed in the global scope`() = timeoutRunBlocking(30.seconds) {
    Assume.assumeTrue(shellPath.name == "zsh")
    val dir = Files.createTempDirectory("zsh-custom-zdotdir")
    Files.writeString(dir.resolve(".zshrc"), """
      export -T MY_PATH my_path
      MY_PATH='path1:path2:path1:path3:path2'
      typeset -U my_path MY_PATH
      echo "MY_PATH=${'$'}MY_PATH"
    """.trimIndent())

    val envs = mapOf("ZDOTDIR" to dir.toString())
    val options = ShellStartupOptions.Builder().envVariables(envs).build()
    val events = startSessionAndCollectOutputEvents(options) {}

    val textToFind = "MY_PATH=path1:path2:path3"
    val output = calculateResultingOutput(events)
    assertThat(output)
      .overridingErrorMessage { "Expected output to contain '$textToFind'.\n${dumpTerminalState(events)}" }
      .contains(textToFind)
    Unit
  }

  /**
   * $# variable value is the number of the parameters passed to the enclosing function.
   * User's shell scripts should be sourced in the global scope instead of the function.
   * So, this variable should be zero during user scripts sourcing.
   */
  @Test
  fun `zsh $# variable is zero during user scripts sourcing`() = timeoutRunBlocking(30.seconds) {
    Assume.assumeTrue(shellPath.name == "zsh")

    val warningText = "variable is not zero"
    val dir = Files.createTempDirectory("zsh-custom-zdotdir")
    Files.writeString(dir.resolve(".zshrc"), """
      if [[ $# -gt 0 ]]; then
        echo "$warningText"
      fi
    """.trimIndent())

    val envs = mapOf("ZDOTDIR" to dir.toString())
    val options = ShellStartupOptions.Builder().envVariables(envs).build()
    val events = startSessionAndCollectOutputEvents(options) {}

    val output = calculateResultingOutput(events)
    assertThat(output)
      .overridingErrorMessage { "Expected output to not contain '$warningText'.\n${dumpTerminalState(events)}" }
      .doesNotContain(warningText)
    Unit
  }

  @Test
  fun `ZDOTDIR can be changed in zshenv`() = timeoutRunBlocking(30.seconds) {
    Assume.assumeTrue(shellPath.name == "zsh")
    val msg = "Loading .zshrc in the updated ZDOTDIR"
    val zshrcDir = Files.createTempDirectory(".zshrc-dir").also {
      it.resolve(".zshrc").writeText("echo '$msg'")
    }
    val zshenvDir = Files.createTempDirectory(".zshenv-dir").also {
      it.resolve(".zshenv").writeText("ZDOTDIR='$zshrcDir'")
    }

    val envs = mapOf("ZDOTDIR" to zshenvDir.toString())
    val options = ShellStartupOptions.Builder().envVariables(envs).build()
    val events = startSessionAndCollectOutputEvents(options) {}

    val output = calculateResultingOutput(events)
    assertThat(output)
      .overridingErrorMessage { "Expected output to contain '$msg'.\n${dumpTerminalState(events)}" }
      .contains(msg)
    Unit
  }

  @Test
  fun `JEDITERM_SOURCE should be loaded after all startup files`() = timeoutRunBlocking(30.seconds) {
    Assume.assumeTrue(shellPath.name == "zsh")
    val customVariableName = "MY_CUSTOM_VARIABLE_NAME"
    val customVariableValue = "MY_CUSTOM_VARIABLE_VALUE"
    val zdotdir = Files.createTempDirectory("zsh-custom-zdotdir")
    // Use .zlogin, because it's loaded last in the Zsh startup files.
    zdotdir.resolve(".zlogin").writeText("$customVariableName='$customVariableValue'")

    val customizer = object : LocalTerminalCustomizer() {
      override fun customizeCommandAndEnvironment(project: Project,
                                                  workingDirectory: String?,
                                                  command: Array<out String>?,
                                                  envs: MutableMap<String?, String?>): Array<out String?>? {
        val file = Files.createTempFile("my-jediterm-source", ".zsh")
        file.writeText("echo $$customVariableName")
        envs["JEDITERM_SOURCE"] = file.toString()
        return command
      }
    }

    ExtensionTestUtil.maskExtensions(
      LocalTerminalCustomizer.EP_NAME,
      listOf(customizer),
      disposableRule.disposable
    )

    val envs = mapOf("ZDOTDIR" to zdotdir.toString())
    val options = ShellStartupOptions.Builder().shellCommand(listOf(shellPath.toString(), "--login")).envVariables(envs).build()
    val events = startSessionAndCollectOutputEvents(options) {}

    val output = calculateResultingOutput(events)
    assertThat(output)
      .overridingErrorMessage { "Expected output to contain '$customVariableValue'.\n${dumpTerminalState(events)}" }
      .contains(customVariableValue)
    Unit
  }

  /**
   * This test may fail locally if you have some custom prompt configured in your Bash configs.
   * Some prompts may be rendered differently in the posix mode.
   */
  @Test
  fun `Output is not affected by enabling posix option in bash`() = timeoutRunBlocking(30.seconds) {
    Assume.assumeTrue(shellPath.name == "bash")

    val terminalInputActions: suspend (SendChannel<TerminalInputEvent>) -> Unit = {
      it.send(TerminalWriteBytesEvent("echo 'abracadabra'".toByteArray()))
      it.send(TerminalWriteBytesEvent(ENTER_BYTES))
    }

    val regularSessionEvents = async {
      startSessionAndCollectOutputEvents(block = terminalInputActions)
    }
    val posixSessionEvents = async {
      val rcFile = Files.createTempFile("terminal", ".rcfile")
      rcFile.writeText("set -o posix")

      val initialCommand = TerminalSessionTestUtil.createShellCommand(shellPath.toString())
      val fullCommand = initialCommand + listOf("--rcfile", rcFile.toString())
      val options = ShellStartupOptions.Builder().shellCommand(fullCommand).build()
      startSessionAndCollectOutputEvents(options, block = terminalInputActions)
    }

    val regularSessionOutput = calculateResultingOutput(regularSessionEvents.await()).trim()
    val posixSessionOutput = calculateResultingOutput(posixSessionEvents.await()).trim()

    // Check that the output of posix and regular sessions is the same
    assertThat(posixSessionOutput).isEqualTo(regularSessionOutput)
    Unit
  }

  private suspend fun startSessionAndCollectOutputEvents(
    options: ShellStartupOptions = ShellStartupOptions.Builder().build(),
    isLowLevelSession: Boolean = false,
    block: suspend (SendChannel<TerminalInputEvent>) -> Unit,
  ): List<TerminalOutputEvent> {
    return coroutineScope {
      val allOptions = if (options.shellCommand != null) {
        options
      }
      else {
        val shellCommand = TerminalSessionTestUtil.createShellCommand(shellPath.toString())
        options.builder().shellCommand(shellCommand).build()
      }

      val session = TerminalSessionTestUtil.startTestTerminalSession(
        projectRule.project,
        allOptions,
        isLowLevelSession,
        childScope("TerminalSession"),
      )
      val inputChannel = session.getInputChannel()

      val outputEvents = mutableListOf<TerminalOutputEvent>()
      val eventsCollectionJob = launch {
        val outputFlow = session.getOutputFlow()
        outputFlow.collect { events ->
          outputEvents.addAll(events)
        }
      }

      // Wait for prompt initialization before going further
      session.awaitOutputEvent(TerminalPromptFinishedEvent)

      block(inputChannel)

      launch(start = CoroutineStart.UNDISPATCHED) {
        // Block the coroutine scope completion until we receive the termination event.
        session.awaitOutputEvent(TerminalSessionTerminatedEvent)
        eventsCollectionJob.cancel()
      }

      delay(1000) // Wait for the shell to handle input sent in `block`

      inputChannel.send(TerminalCloseEvent())

      outputEvents
    }
  }

  private fun calculateResultingOutput(events: List<TerminalOutputEvent>): String {
    val outputModel = TerminalTestUtil.createOutputModel(maxLength = Int.MAX_VALUE)

    val initialState = events.find { it is TerminalInitialStateEvent }
    if (initialState is TerminalInitialStateEvent) {
      outputModel.restoreFromState(initialState.outputModelState.toState())
    }

    events
      .filterIsInstance<TerminalContentUpdatedEvent>()
      .map { event ->
        val styles = event.styles.map { it.toStyleRange() }
        outputModel.updateContent(event.startLineLogicalIndex, event.text, styles)
      }

    return outputModel.document.text
  }

  private fun dumpTerminalState(events: List<TerminalOutputEvent>): String {
    return """
      |Output:
      |${calculateResultingOutput(events)}
      |-------------------------------------------------------------
      |All events:
      |${events.joinToString("\n")}
      """.trimMargin()
  }

  private fun assertSameEvents(
    actual: List<TerminalOutputEvent>,
    expected: List<KClass<out TerminalShellIntegrationEvent>>,
    eventsToLog: List<TerminalOutputEvent>,
  ) {
    fun List<TerminalOutputEvent>.asString(): String {
      return joinToString("\n")
    }

    val errorMessage = {
      """
        |Expected:
        |${expected}
        |-------------------------------------------------------------
        |But was:
        |${actual.map { it::class }}
        |-------------------------------------------------------------
        |${dumpTerminalState(eventsToLog)}
      """.trimMargin()
    }

    assertThat(actual.map { it::class })
      .overridingErrorMessage(errorMessage)
      .isEqualTo(expected)
  }

  private val CTRL_C_BYTES: ByteArray = byteArrayOf(Ascii.ETX)
  private val CTRL_L_BYTES: ByteArray = byteArrayOf(Ascii.FF)
  private val TAB_BYTES: ByteArray = byteArrayOf(Ascii.HT)
}