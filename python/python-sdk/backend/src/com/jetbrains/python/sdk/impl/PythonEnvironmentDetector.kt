// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.impl

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.PythonEnvironment
import com.jetbrains.python.sdk.PythonEnvironmentProvider
import com.jetbrains.python.sdk.impl.PySdkBundle.message
import kotlin.io.path.isExecutable

/**
 * Detects the Python environment from the file system layout around this binary.
 *
 * Asks each [PythonEnvironmentProvider] in the registered order and takes the first answer. The system provider is
 * registered last and claims any layout, so a caller always gets an environment.
 *
 * Returns an error if the binary is not executable, or if a provider owns the layout but the layout is broken.
 */
@RequiresBackgroundThread
internal fun PythonBinary.detectPythonEnvironmentImpl(): PyResult<PythonEnvironment> {
  if (!isExecutable()) return PyResult.localizedError(message("python.sdk.detect.binary.not.executable", this))

  return PythonEnvironmentProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.instance.detect(this) }
         ?: error("No ${PythonEnvironmentProvider.EP_NAME.name} claimed $this. The system provider claims any layout, so it is not registered.")
}

/**
 * A system-wide Python installation: whatever no other provider claims.
 *
 * Register it last, because it answers for any layout.
 */
internal class SystemPythonEnvironmentProvider : PythonEnvironmentProvider {
  override val environmentClass: Class<out PythonEnvironment> = PythonEnvironment.SystemPython::class.java

  override fun detect(pythonBinary: PythonBinary): PyResult<PythonEnvironment> =
    PythonEnvironment.SystemPython(pythonBinaryPath = pythonBinary).let { PyResult.success(it) }
}
