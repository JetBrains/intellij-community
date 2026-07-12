// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.frontend.session.TransferableTerminalLifetime
import com.intellij.terminal.frontend.session.createStandardStateAwareTerminalSession
import com.intellij.terminal.frontend.session.createTransferableTerminalSessionForTest
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.impl.TerminalContentUpdatedEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInitialStateEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalInputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalOutputEvent
import org.jetbrains.plugins.terminal.session.impl.TerminalSession
import org.jetbrains.plugins.terminal.session.impl.TerminalStartupOptionsImpl
import org.jetbrains.plugins.terminal.startup.TerminalProcessType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@TestApplication
class TransferableTerminalLifetimeTest {
  private val project: Project by projectFixture()

  @Test
  fun sourceOwnerCancellationAfterRebindLeavesRuntimeAndDestinationBindingActive() = runBlocking(Dispatchers.Unconfined) {
    val applicationScope = childScope("test application")
    val sourceOwnerScope = childScope("test source project")
    val destinationOwnerScope = childScope("test destination project")
    val lifetime = TransferableTerminalLifetime(applicationScope)

    val sourceBinding = lifetime.replaceBinding(sourceOwnerScope) { bindingScope -> bindingScope }
    val destinationBinding = lifetime.replaceBinding(destinationOwnerScope) { bindingScope -> bindingScope }
    sourceOwnerScope.cancel()

    assertThat(sourceBinding.value.isActive).isFalse()
    assertThat(lifetime.runtimeScope.isActive).isTrue()
    assertThat(destinationBinding.value.isActive).isTrue()

    lifetime.close()

    assertThat(lifetime.runtimeScope.isActive).isFalse()
    assertThat(destinationBinding.value.isActive).isFalse()
    destinationOwnerScope.cancel()
    applicationScope.cancel()
  }

  @Test
  fun failedBindingCreationCancelsTemporaryScopeAndPreservesCurrentRuntime() = runBlocking(Dispatchers.Unconfined) {
    val applicationScope = childScope("test application")
    val sourceOwnerScope = childScope("test source project")
    val destinationOwnerScope = childScope("test destination project")
    val lifetime = TransferableTerminalLifetime(applicationScope)
    lateinit var failedBindingScope: CoroutineScope
    try {
      val sourceBinding = lifetime.replaceBinding(sourceOwnerScope) { bindingScope -> bindingScope }

      val failure = runCatching {
        lifetime.replaceBinding(destinationOwnerScope) { bindingScope ->
          failedBindingScope = bindingScope
          error("binding creation failed")
        }
      }

      assertThat(failure.exceptionOrNull()).hasMessage("binding creation failed")
      assertThat(failedBindingScope.isActive).isFalse()
      assertThat(sourceBinding.value.isActive).isTrue()
      assertThat(lifetime.runtimeScope.isActive).isTrue()
    }
    finally {
      lifetime.close()
      sourceOwnerScope.cancel()
      destinationOwnerScope.cancel()
      applicationScope.cancel()
    }
    Unit
  }

  @Test
  fun immediatelyCompletedRuntimeCannotRegressTerminationOrEscapeConstructor() = runBlocking(Dispatchers.Unconfined) {
    val applicationScope = childScope("test application")
    val transitions = mutableListOf<TerminalViewSessionState>()
    lateinit var runtimeScope: CoroutineScope
    try {
      val result = runCatching {
        createTransferableTerminalSessionForTest(
          parentScope = applicationScope,
          initialProject = project,
          sessionStarter = { startedScope ->
            runtimeScope = startedScope
            startedScope.cancel()
            TestRawTerminalSession(startedScope)
          },
          stateTransitionObserver = transitions::add,
        )
      }

      assertThat(result.exceptionOrNull()).hasMessage("Transferable terminal runtime terminated during startup")
      assertThat(result.getOrNull()).isNull()
      assertThat(runtimeScope.isActive).isFalse()
      assertThat(transitions).containsExactly(
        TerminalViewSessionState.NotStarted,
        TerminalViewSessionState.Terminated,
      )
    }
    finally {
      applicationScope.cancel()
    }
    Unit
  }

  @Test
  @Timeout(20)
  fun destinationCollectorReceivesStandardInitialStateForSameRuntime() = runBlocking(Dispatchers.Unconfined) {
    val applicationScope = childScope("test application")
    val sourceOwnerScope = childScope("test source project")
    val destinationOwnerScope = childScope("test destination project")
    val lifetime = TransferableTerminalLifetime(applicationScope)
    try {
      val rawSession = TestRawTerminalSession(lifetime.runtimeScope)
      val session = createStandardStateAwareTerminalSession(
        delegate = rawSession,
        startupOptions = TerminalStartupOptionsImpl(
          shellCommand = listOf("test-shell"),
          workingDirectory = "/test/project",
          envVariables = emptyMap(),
          processType = TerminalProcessType.SHELL,
          pid = TEST_PROCESS_ID,
        ),
        scope = lifetime.runtimeScope,
      )
      rawSession.emit(
        TerminalContentUpdatedEvent(
          text = "",
          styles = emptyList(),
          startLineLogicalIndex = 0,
          cursorLogicalLineIndex = 0,
          cursorColumnIndex = 0,
        )
      )
      yield()
      val sourceBinding = lifetime.replaceBinding(sourceOwnerScope) { bindingScope -> bindingScope }
      val sourceInitial = session.getOutputFlow().first().single() as TerminalInitialStateEvent
      assertThat(sourceInitial.outputModelState.text).isEmpty()

      rawSession.emit(
        TerminalContentUpdatedEvent(
          text = "retained transcript",
          styles = emptyList(),
          startLineLogicalIndex = 0,
          cursorLogicalLineIndex = 0,
          cursorColumnIndex = 19,
        )
      )
      yield()
      val destinationBinding = lifetime.replaceBinding(destinationOwnerScope) { bindingScope -> bindingScope }
      sourceOwnerScope.cancel()
      val destinationInitial = session.getOutputFlow().first().single() as TerminalInitialStateEvent

      assertThat(destinationInitial.outputModelState.text).isEqualTo("retained transcript")
      assertThat(destinationInitial.startupOptions.shellCommand).containsExactly("test-shell")
      assertThat(session.processId).isEqualTo(TEST_PROCESS_ID)
      assertThat(sourceBinding.value.isActive).isFalse()
      assertThat(destinationBinding.value.isActive).isTrue()
      assertThat(lifetime.runtimeScope.isActive).isTrue()

      lifetime.close()

      assertThat(lifetime.runtimeScope.isActive).isFalse()
      assertThat(destinationBinding.value.isActive).isFalse()
    }
    finally {
      lifetime.close()
      sourceOwnerScope.cancel()
      destinationOwnerScope.cancel()
      applicationScope.cancel()
    }
    Unit
  }
}

private class TestRawTerminalSession(
  override val coroutineScope: CoroutineScope,
) : TerminalSession {
  private val input = Channel<TerminalInputEvent>(Channel.UNLIMITED)
  private val output = MutableSharedFlow<List<TerminalOutputEvent>>(replay = 1, extraBufferCapacity = 8)

  suspend fun emit(event: TerminalOutputEvent) {
    output.emit(listOf(event))
  }

  override suspend fun getInputChannel(): SendChannel<TerminalInputEvent> = input

  override suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>> = output

  override val eelDescriptor: EelDescriptor = LocalEelDescriptor
  override val processId: Long = TEST_PROCESS_ID
  override val isClosed: Boolean
    get() = !coroutineScope.isActive

  override suspend fun hasRunningCommands(): Boolean = false
}

private const val TEST_PROCESS_ID = 42L
