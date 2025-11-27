// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.ui

import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import com.jetbrains.python.sdk.impl.PySdkBundle
import kotlinx.coroutines.runBlocking

/**
 * Runs [code] in background under the modal dialog
 */
@RequiresEdt
@RequiresBlockingContext
fun <T> pyModalBlocking(modalTaskOwner: ModalTaskOwner = ModalTaskOwner.guess(), code: suspend () -> T): T =
  runWithModalProgressBlocking(modalTaskOwner, PySdkBundle.message("python.sdk.run.wait"), TaskCancellation.nonCancellable()) {
    code.invoke()
  }


/**
 * It is *not* recommended to use this function. Prefer suspend functions.
 */
internal fun <T> pyMayBeModalBlocking(modalTaskOwner: ModalTaskOwner = ModalTaskOwner.guess(), code: suspend () -> T): T =
  if (EDT.isCurrentThreadEdt()) {
    pyModalBlocking(modalTaskOwner, code)
  }
  else {
    runBlocking { code() }
  }
