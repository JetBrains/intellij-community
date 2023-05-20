// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.CoroutineScope

@RequiresBackgroundThread
@RequiresBlockingContext
fun <T> runBlockingCancellableUnderIndicator(action: suspend CoroutineScope.() -> T): T {
  val process: () -> T = {
    runBlockingCancellable {
      return@runBlockingCancellable action()
    }
  }
  return ProgressManager.getInstance().runProcess(process, EmptyProgressIndicator())
}