// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin.utils

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import org.jetbrains.concurrency.AsyncPromise

class MavenCommandsExecutionListener(val promise: AsyncPromise<Any?>) : ExecutionListener {
  override fun processNotStarted(executorId: String, env: ExecutionEnvironment, cause: Throwable?) {
    if (cause != null) {
      promise.setError(cause)
    }
  }

  override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
    if (exitCode != 0) {
      promise.setError("Process finished with code exit code $exitCode")
    }
    else {
      promise.setResult(null)
    }
  }
}