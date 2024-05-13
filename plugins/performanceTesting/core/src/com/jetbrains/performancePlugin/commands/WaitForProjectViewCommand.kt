package com.jetbrains.performancePlugin.commands

import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.projectView.impl.ProjectViewListener
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.util.Ref
import com.jetbrains.performancePlugin.PerformanceTestSpan
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import kotlinx.coroutines.sync.Mutex

/**
 * Wait till a project tree is fully initialized.
 * projectView#cachedNodesLoaded might be missing if there are not cached nodes
 * Command should be executed as soon as possible since project view initialization happens very early and the command might hang.
 */
class WaitForProjectViewCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: String = CMD_PREFIX + "waitForProjectView"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val connection = context.project.messageBus.connect()
    val mutex = Mutex(true)
    val mainSpan = Ref<Span>()
    val cachedNodesSpan = Ref<Span>()
    val paneShownSpan = Ref<Span>()
    connection.subscribe(ProjectViewListener.TOPIC, object : ProjectViewListener {
      override fun initStarted() {
        mainSpan.set(PerformanceTestSpan.TRACER.spanBuilder("projectView").startSpan())
        cachedNodesSpan.set(PerformanceTestSpan.TRACER.spanBuilder("projectView#cachedNodesLoaded").setParent(Context.current().with(mainSpan.get())).startSpan())
        paneShownSpan.set(PerformanceTestSpan.TRACER.spanBuilder("projectView#paneShown").setParent(Context.current().with(mainSpan.get())).startSpan())
      }

      override fun paneShown(current: AbstractProjectViewPane, previous: AbstractProjectViewPane?) {
        paneShownSpan.get()?.end()
      }

      override fun initCachedNodesLoaded() {
        cachedNodesSpan.get()?.end()
      }

      override fun initCompleted() {
        mainSpan.get()?.end()
        mutex.unlock()
      }
    })
    mutex.lock()
    connection.disconnect()
  }
}