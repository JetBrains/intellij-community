// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.callUnderOwnClassLoader
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.performancePlugin.commands.CodeAnalysisStateListener.HighlightedEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Each test makes [CodeAnalysisStateListener] track a text editor, disposes the editor the way a closed tab does, and then waits for the
 * code analysis. The wait ends at once only when the release of the editor ends what the listener tracks for it.
 */
@TestApplication
internal class CodeAnalysisStateListenerTest {
  private val projectFixture = projectFixture()

  @Test
  fun `a disposed editor ends its highlighting session`(): Unit = timeoutRunBlocking {
    val listener = projectFixture.get().service<CodeAnalysisStateListener>()
    trackAndDispose { listener.registerDaemonStarted(listOf(it)) }
    listener.waitAnalysisToFinish(timeout = 5.seconds, throws = true, logsError = false)
  }

  @Test
  fun `a wait after a daemon pass finished returns the moment the pass finished`(): Unit = timeoutRunBlocking {
    val listener = projectFixture.get().service<CodeAnalysisStateListener>()
    // a pass started in dumb mode is not final, so the listener would wait for the next one
    IndexingTestUtil.suspendUntilIndexesAreReady(projectFixture.get())
    val passStartedAt = System.nanoTime()
    trackAndDispose { editor ->
      listener.registerDaemonStarted(listOf(editor))
      listener.registerDaemonFinishedOrCancelled(mapOf(editor to highlighted(editor)), "finished", UUID.randomUUID())
    }
    val waitStartedAt = System.nanoTime()
    val finishedAt = listener.waitAnalysisToFinish(timeout = 5.seconds, throws = true, logsError = false)
    assertTrue(finishedAt != null && finishedAt - passStartedAt >= 0 && finishedAt - waitStartedAt <= 0,
               "finished at $finishedAt, the pass started at $passStartedAt, the wait started at $waitStartedAt")
  }

  @Test
  fun `a wait that starts during the analysis returns the moment the pass finished`(): Unit = timeoutRunBlocking {
    val listener = projectFixture.get().service<CodeAnalysisStateListener>()
    // a pass started in dumb mode is not final, so the listener would wait for the next one
    IndexingTestUtil.suspendUntilIndexesAreReady(projectFixture.get())
    val editor = createTextEditor(LightVirtualFile("tracked.txt", "text"))
    try {
      withContext(Dispatchers.UiWithModelAccess) { listener.registerDaemonStarted(listOf(editor)) }
      val wait = async(Dispatchers.Default) { listener.waitAnalysisToFinish(timeout = 5.seconds, throws = true, logsError = false) }
      // the waiter registers after a round trip to the EDT; a later registration returns the same moment by the unlocked path
      delay(500.milliseconds)
      assertFalse(wait.isCompleted, "the wait ended while the analysis was in progress")
      val passFinishesAt = System.nanoTime()
      withContext(Dispatchers.UiWithModelAccess) {
        listener.registerDaemonFinishedOrCancelled(mapOf(editor to highlighted(editor)), "finished", UUID.randomUUID())
      }
      val finishedAt = wait.await()
      val waitEndedAt = System.nanoTime()
      assertTrue(finishedAt != null && finishedAt - passFinishesAt >= 0 && finishedAt - waitEndedAt <= 0,
                 "finished at $finishedAt, the pass finished after $passFinishesAt, the wait ended at $waitEndedAt")
    }
    finally {
      withContext(Dispatchers.UiWithModelAccess) { Disposer.dispose(editor) }
    }
  }

  @Test
  fun `the release of a tracked editor is no finished analysis`(): Unit = timeoutRunBlocking {
    val listener = projectFixture.get().service<CodeAnalysisStateListener>()
    trackAndDispose { listener.registerOpenedEditors(listOf(it)) }
    assertNull(listener.waitAnalysisToFinish(timeout = 5.seconds, throws = true, logsError = false))
  }

  @Test
  fun `a disposed editor ends the wait for its file`(): Unit = timeoutRunBlocking {
    val listener = projectFixture.get().service<CodeAnalysisStateListener>()
    trackAndDispose { listener.registerOpenedEditors(listOf(it)) }
    listener.waitAnalysisToFinish(timeout = 5.seconds, throws = true, logsError = false)
  }

  @Test
  fun `a highlighting session keeps no class loader of the code that started the daemon`(): Unit = timeoutRunBlocking {
    val listener = projectFixture.get().service<CodeAnalysisStateListener>()
    trackAndDispose { editor ->
      callUnderOwnClassLoader { listener.registerDaemonStarted(listOf(editor)) }.loader.ensureCollected()
    }
    listener.waitAnalysisToFinish(timeout = 5.seconds, throws = true, logsError = false)
  }

  @Test
  fun `the release of a second main editor of the file keeps the wait for the file`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val listener = project.service<CodeAnalysisStateListener>()
    val file = LightVirtualFile("tracked.txt", "text")
    val tracked = createTextEditor(file)
    try {
      withContext(Dispatchers.UiWithModelAccess) {
        listener.registerOpenedEditors(listOf(tracked))
        Disposer.dispose(createTextEditor(file))
      }
      assertWaitGoesOn(listener)
    }
    finally {
      withContext(Dispatchers.UiWithModelAccess) { Disposer.dispose(tracked) }
    }
    listener.waitAnalysisToFinish(timeout = 5.seconds, throws = true, logsError = false)
  }

  @Test
  fun `the release of an editor that is not a main editor keeps the wait for the file`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val listener = project.service<CodeAnalysisStateListener>()
    val tracked = createTextEditor(LightVirtualFile("tracked.txt", "text"))
    try {
      withContext(Dispatchers.UiWithModelAccess) {
        listener.registerOpenedEditors(listOf(tracked))
        val factory = EditorFactory.getInstance()
        factory.releaseEditor(factory.createEditor(tracked.editor.document, project))
      }
      assertWaitGoesOn(listener)
    }
    finally {
      withContext(Dispatchers.UiWithModelAccess) { Disposer.dispose(tracked) }
    }
    listener.waitAnalysisToFinish(timeout = 5.seconds, throws = true, logsError = false)
  }

  private fun highlighted(editor: TextEditor): HighlightedEditor {
    return object : HighlightedEditor {
      override val editor = editor
      override val shouldWaitForNextHighlighting = false
    }
  }

  private suspend fun createTextEditor(file: LightVirtualFile): TextEditor {
    val project = projectFixture.get()
    return withContext(Dispatchers.UiWithModelAccess) {
      writeIntentReadAction { TextEditorProvider.getInstance().createEditor(project, file) as TextEditor }
    }
  }

  private suspend fun assertWaitGoesOn(listener: CodeAnalysisStateListener) {
    val failure = runCatching { listener.waitAnalysisToFinish(timeout = 1.seconds, throws = true, logsError = false) }.exceptionOrNull()
    assertInstanceOf(TimeoutException::class.java, failure)
  }

  private suspend fun trackAndDispose(track: (TextEditor) -> Unit) {
    val project = projectFixture.get()
    withContext(Dispatchers.UiWithModelAccess) {
      val editor = writeIntentReadAction {
        TextEditorProvider.getInstance().createEditor(project, LightVirtualFile("tracked.txt", "text")) as TextEditor
      }
      try {
        track(editor)
      }
      finally {
        Disposer.dispose(editor)
      }
    }
  }
}
