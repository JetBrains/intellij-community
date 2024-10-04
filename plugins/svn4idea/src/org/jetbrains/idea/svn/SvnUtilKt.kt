// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn

import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.idea.svn.SvnUtil.getWcDb
import java.util.concurrent.atomic.AtomicBoolean

internal fun putWcDbFilesToVfs(infos: Collection<RootUrlInfo>) {
  val wcDbFiles = infos
    .asSequence()
    .filter { it.format.isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN) }
    .filter { NestedCopyType.switched != it.type }
    .map { getWcDb(it.ioFile) }
    .toList()

  LocalFileSystem.getInstance().refreshIoFiles(wcDbFiles, true, false, null)
}

internal fun <T : Any> computeAfterUpdateChanges(project: Project, parent: CoroutineScope, block: () -> T): T? {
  val promise = AsyncPromise<T>()
  val indicator = DelegatingProgressIndicator()
  val afterUpdateStarted = AtomicBoolean()
  val afterUpdateTracker = t@{ // `afterUpdate` could be not called if a project is disposed => treat dispose state manually
    if (promise.isDone) return@t

    if (indicator.isRunning) {
      indicator.cancel()
    }
    if (!afterUpdateStarted.get()) {
      promise.cancel()
    }
  }

  val handle = parent.coroutineContext.job.invokeOnCompletion {
    afterUpdateTracker()
  }

  try {
    ChangeListManager.getInstance(project).invokeAfterUpdate(false) {
      try {
        afterUpdateStarted.set(true)
        indicator.checkCanceled()

        ProgressManager.getInstance().runProcess(
          { promise.setResult(block()) },
          indicator
        )
      }
      catch (_: ProcessCanceledException) {
        promise.cancel()
      }
      catch (e: Throwable) {
        promise.setError(e)
      }
    }
    return promise.get()
  }
  finally {
    handle.dispose()
  }
}