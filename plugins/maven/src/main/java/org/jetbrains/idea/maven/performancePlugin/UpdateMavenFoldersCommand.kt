// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin

import com.intellij.build.SyncViewManager
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FinishEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.Disposer
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.project.MavenFolderResolver
import java.util.concurrent.CountDownLatch

/**
 * The command is the same as click 'Generate sources and Update folders for maven project'
 * Syntax: %updateMavenFolders
 */
class UpdateMavenFoldersCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "updateMavenFolders"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  @Service(Service.Level.PROJECT)
  private class CoroutineService(val coroutineScope: CoroutineScope)

  override suspend fun doExecute(context: PlaybackContext) {
    val disposable = Disposer.newDisposable()
    try {
      val latch = CountDownLatch(1)
      context.project.also {
        val isErrorExpected = extractCommandArgument(PREFIX).toBoolean()
        it.getService(SyncViewManager::class.java).addListener(
          { buildId, event ->
            if (event is MessageEventImpl && event.kind == MessageEvent.Kind.ERROR) {
              if (!isErrorExpected) throw IllegalStateException("Error is not expected bu happened: ${event.message}")
              latch.countDown()
            }
            else if (event is FinishEventImpl) {
              if (isErrorExpected) throw IllegalStateException("Error is expected bu didn't happen")
              latch.countDown()
            }
          }, disposable)
        it.service<CoroutineService>().coroutineScope.launch {
          MavenFolderResolver(it).resolveFoldersAndImport()
        }
        latch.await()
      }
    }
    finally {
      Disposer.dispose(disposable)
    }

  }

  override fun getName(): String {
    return NAME
  }
}