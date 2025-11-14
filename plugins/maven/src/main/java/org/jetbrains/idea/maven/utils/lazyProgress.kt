// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.reportRawProgress
import kotlinx.coroutines.*
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> CoroutineScope.withLazyProgressIndicator(
  project: Project,
  delay: Duration,
  title: @ProgressTitle String,
  cancellable: Boolean,
  action: suspend (reporter: RawProgressReporter) -> T,
): T {
  val reporter = DelegatingReporter()
  val end = System.currentTimeMillis() + delay.inWholeMilliseconds
  val actionJob = async { action(reporter) }
  while (actionJob.isActive && System.currentTimeMillis() < end) {
    delay(200)
  }
  if (actionJob.isCompleted) {
    return actionJob.getCompleted()
  }
  return try {
    withBackgroundProgress(project, title, cancellable) {
      reportRawProgress { realReporter ->
        reporter.setDelegate(realReporter)
        return@withBackgroundProgress actionJob.await()
      }
    }
  }
  catch (cancellation: CancellationException) {
    actionJob.cancel(cancellation)
    throw cancellation
  }
}

private class DelegatingReporter : RawProgressReporter {

  @Volatile private var delegate: RawProgressReporter? = null

  fun setDelegate(delegate: RawProgressReporter) {
    this.delegate = delegate
  }

  override fun fraction(fraction: Double?) {
    delegate?.fraction(fraction)
  }

  override fun details(details: @NlsContexts.ProgressDetails String?) {
    delegate?.details(details)
  }

  override fun text(text: @NlsContexts.ProgressText String?) {
    delegate?.text(text)
  }
}