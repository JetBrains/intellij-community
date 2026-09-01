package com.intellij.python.sdk.backend

import com.intellij.openapi.components.service
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.osFamily
import com.intellij.python.sdk.backend.service.ActivatableEnvironmentService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.errorProcessing.PyResult
import com.intellij.python.sdk.backend.PySdkBundle.message
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlin.io.path.isExecutable
import kotlin.io.path.name


@RequiresBackgroundThread
fun PythonBinary.resolvePythonHome(): PythonHomePath = when (osFamily) {
  EelOsFamily.Windows -> parent.takeIf { it.name.lowercase() != "scripts" } ?: parent.parent
  EelOsFamily.Posix -> parent.takeIf { it.name != "bin" } ?: parent.parent
}

@RequiresBackgroundThread
fun PythonHomePath.resolvePythonBinary(): PythonBinary? {
  return VirtualEnvReader().findPythonInPythonRoot(this)
}


/**
 * Detects the Python environment from the file system layout around this binary.
 *
 * Each kind is detected by its own [PythonEnvironmentProvider], so this function names no kind. A binary that no
 * other provider claims is a [SystemPythonEnvironment].
 *
 * Returns an error if the binary does not exist or is not executable, or if a provider owns the layout but the
 * layout is broken.
 */
@RequiresBackgroundThread
fun PythonBinary.detectPythonEnvironment(): PyResult<PythonEnvironment> {
  if (!isExecutable()) return PyResult.localizedError(message("python.sdk.detect.binary.not.executable", this))

  return PythonEnvironmentProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.detect(this) }
         ?: error("No ${PythonEnvironmentProvider.EP_NAME.name} claimed $this. The system provider claims any layout, so it is not registered.")
}

/** The activation environment for the interpreter at [this] path (its environment is detected on a cache miss). */
suspend fun PythonBinary.activationEnvironment(): PyResult<Map<String, String>> =
  service<ActivatableEnvironmentService>().activationEnvironment(this)
