// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.extensions.python

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import javax.swing.SwingUtilities

fun <T> ProgressManager.runUnderProgress(title: String, code: () -> T): T {
  if (SwingUtilities.isEventDispatchThread()) {
    return run(object : Task.WithResult<T, Exception>(null, title, false) {
      override fun compute(indicator: ProgressIndicator) = code()
    })
  }
  else {
    return code()
  }
}
