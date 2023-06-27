// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun performInBackground(action: suspend () -> Unit) {
  if (ApplicationManager.getApplication().isDispatchThread && !ApplicationManager.getApplication().isUnitTestMode) {
    AppExecutorUtil.getAppExecutorService().execute {
      runBlockingMaybeCancellable {
        action()
      }
    }
  }
  else {
    runBlockingMaybeCancellable {
      action()
    }
  }
}