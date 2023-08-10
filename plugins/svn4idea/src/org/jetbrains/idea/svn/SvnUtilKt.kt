// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn

import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.idea.svn.SvnUtil.getWcDb
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors.toList

internal fun putWcDbFilesToVfs(infos: Collection<RootUrlInfo>) {
  val wcDbFiles = infos.stream()
    .filter { it.format.isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN) }
    .filter { NestedCopyType.switched != it.type }
    .map { it.ioFile }
    .map { getWcDb(it) }
    .collect(toList())

  LocalFileSystem.getInstance().refreshIoFiles(wcDbFiles, true, false, null)
}

internal fun <T : Any> computeAfterUpdateChanges(project: Project, parent: Disposable, block: () -> T): T? {
  val promise = AsyncPromise<T>()
  val indicator = DelegatingProgressIndicator()
  val afterUpdateStarted = AtomicBoolean()
  val afterUpdateTracker = Disposable { // `afterUpdate` could be not called if project is disposed => treat dispose state manually
    if (promise.isDone) return@Disposable

    if (indicator.isRunning) indicator.cancel()
    if (!afterUpdateStarted.get()) promise.cancel()
  }

  Disposer.register(parent, afterUpdateTracker)
  ChangeListManager.getInstance(project).invokeAfterUpdate(false) {
    try {
      afterUpdateStarted.set(true)
      indicator.checkCanceled()

      ProgressManager.getInstance().runProcess(
        { promise.setResult(block()) },
        indicator
      )
    }
    catch (e: ProcessCanceledException) {
      promise.cancel()
    }
    catch (e: Throwable) {
      promise.setError(e)
    }
  }

  return try {
    promise.get()
  }
  finally {
    Disposer.dispose(afterUpdateTracker)
  }
}