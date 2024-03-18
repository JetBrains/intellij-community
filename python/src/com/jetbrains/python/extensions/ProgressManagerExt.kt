// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.extensions

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.NlsContexts
import javax.swing.SwingUtilities

fun <T> ProgressManager.runUnderProgress(@NlsContexts.DialogTitle title: String, code: () -> T): T =
  if (SwingUtilities.isEventDispatchThread()) {
    run(object : Task.WithResult<T, Exception>(null, title, false) {
      override fun compute(indicator: ProgressIndicator) = code()
    })
  }
  else {
    code()
  }
