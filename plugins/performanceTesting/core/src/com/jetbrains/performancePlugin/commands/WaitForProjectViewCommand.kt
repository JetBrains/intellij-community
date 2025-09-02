// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.ide.projectView.impl.ProjectViewListener
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.sync.Mutex

/**
 * Wait till a project tree is fully initialized.
 * Command should be executed as soon as possible since project view initialization happens very early and the command might hang.
 */
class WaitForProjectViewCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: String = CMD_PREFIX + "waitForProjectView"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val connection = context.project.messageBus.connect()
    val mutex = Mutex(true)
    connection.subscribe(ProjectViewListener.TOPIC, object : ProjectViewListener {
      override fun initCompleted() {
        mutex.unlock()
      }
    })
    mutex.lock()
    connection.disconnect()
  }
}