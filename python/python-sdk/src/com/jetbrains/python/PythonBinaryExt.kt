package com.jetbrains.python

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.venvReader.VirtualEnvReader
import org.jetbrains.annotations.ApiStatus
import kotlin.io.path.name


@RequiresBackgroundThread
@ApiStatus.Internal
fun PythonBinary.resolvePythonHome(): PythonHomePath = when (getEelDescriptor().platform) {
  is EelPlatform.Windows -> parent.takeIf { it.name.lowercase() != "scripts" } ?: parent.parent
  is EelPlatform.Posix -> parent.takeIf { it.name != "bin" } ?: parent.parent
}

@RequiresBackgroundThread
@ApiStatus.Internal
fun PythonHomePath.resolvePythonBinary(): PythonBinary? {
  return VirtualEnvReader(isWindows = getEelDescriptor().platform is EelPlatform.Windows).findPythonInPythonRoot(this)
}
