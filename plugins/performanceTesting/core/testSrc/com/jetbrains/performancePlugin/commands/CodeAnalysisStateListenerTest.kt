// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
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
  fun `a disposed editor ends the wait for its file`(): Unit = timeoutRunBlocking {
    val listener = projectFixture.get().service<CodeAnalysisStateListener>()
    trackAndDispose { listener.registerOpenedEditors(listOf(it)) }
    listener.waitAnalysisToFinish(timeout = 5.seconds, throws = true, logsError = false)
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
