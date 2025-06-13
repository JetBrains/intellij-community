package com.jetbrains.python

import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.venvReader.VirtualEnvReader
import org.jetbrains.annotations.ApiStatus
import kotlin.io.path.name


@RequiresBackgroundThread
@ApiStatus.Internal
fun PythonBinary.resolvePythonHome(): PythonHomePath = when (getEelDescriptor().osFamily) {
  EelOsFamily.Windows -> parent.takeIf { it.name.lowercase() != "scripts" } ?: parent.parent
  EelOsFamily.Posix -> parent.takeIf { it.name != "bin" } ?: parent.parent
}

@RequiresBackgroundThread
@ApiStatus.Internal
fun PythonHomePath.resolvePythonBinary(): PythonBinary? {
  return VirtualEnvReader(isWindows = getEelDescriptor().osFamily.isWindows).findPythonInPythonRoot(this)
}
