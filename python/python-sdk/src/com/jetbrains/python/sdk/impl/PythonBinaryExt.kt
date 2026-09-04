package com.jetbrains.python.sdk.impl

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.venvReader.VirtualEnvReader
import org.jetbrains.annotations.ApiStatus


@RequiresBackgroundThread
@ApiStatus.Internal
fun PythonHomePath.resolvePythonBinary(): PythonBinary? {
  return VirtualEnvReader().findPythonInPythonRoot(this)
}
