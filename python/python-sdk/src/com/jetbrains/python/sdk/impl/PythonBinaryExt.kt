package com.jetbrains.python.sdk.impl

import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.osFamily
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.venvReader.VirtualEnvReader
import org.jetbrains.annotations.ApiStatus
import kotlin.io.path.name


@RequiresBackgroundThread
@ApiStatus.Internal
fun PythonBinary.resolvePythonHome(): PythonHomePath = when (osFamily) {
  EelOsFamily.Windows -> parent.takeIf { it.name.lowercase() != "scripts" } ?: parent.parent
  EelOsFamily.Posix -> parent.takeIf { it.name != "bin" } ?: parent.parent
}

@RequiresBackgroundThread
@ApiStatus.Internal
fun PythonHomePath.resolvePythonBinary(): PythonBinary? {
  return VirtualEnvReader().findPythonInPythonRoot(this)
}
