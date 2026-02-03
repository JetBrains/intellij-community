// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import com.intellij.python.community.execService.ProcessInteractiveHandler
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.PyProcessListener
import com.intellij.python.community.execService.processSemiInteractiveHandler
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun <T> transformerToHandler(
  procListener: PyProcessListener?,
  processOutputTransformer: ProcessOutputTransformer<T>,
): ProcessInteractiveHandler<T> = processSemiInteractiveHandler(procListener) { _, result ->
  processOutputTransformer(result.await())
}