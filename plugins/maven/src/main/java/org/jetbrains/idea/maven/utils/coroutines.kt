// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * [runBlockingCancellable] requires a progress indicator.
 * This method creates an indicator and calls [runBlockingCancellable].
 * Otherwise [runBlockingCancellable] would throw [IllegalStateException].
 */
@RequiresBackgroundThread
@RequiresBlockingContext
@ApiStatus.Experimental
fun <T> runBlockingCancellableUnderIndicator(action: suspend CoroutineScope.() -> T): T = runBlockingMaybeCancellable(action)

@ApiStatus.Experimental
fun performInBackground(action: suspend () -> Unit) {
  if (ApplicationManager.getApplication().isDispatchThread && !ApplicationManager.getApplication().isUnitTestMode) {
    AppExecutorUtil.getAppExecutorService().execute {
      runBlockingCancellableUnderIndicator {
        action()
      }
    }
  }
  else {
    runBlockingCancellableUnderIndicator {
      action()
    }
  }
}