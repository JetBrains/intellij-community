// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyNames
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Deprecated("try to avoid Sdk API usage, use PythonInterpreter extensions instead", ReplaceWith("PythonInterpreterExt.kt"))
fun PythonInterpreter.getSdkAPI(): Sdk = sdk

val PythonInterpreter.presentation: PythonInterpreterPresentation
  @Internal
  get() = sdk.pyInterpreterPresentation()

/**
 * The Python `lib/` directory backing this SDK, or `null` when it cannot be located.
 *
 * For a [PythonEnvironment.Venv] this returns the venv's own lib root via [venvLibDirectory];
 * for any other environment (or an unknown one) it returns the interpreter's standard library
 * directory via [stdlibLibDirectory].
 */
@RequiresBackgroundThread
private fun PythonInterpreter.libDirectory(): VirtualFile? = when (pythonEnvironment) {
  is PythonEnvironment.Venv -> venvLibDirectory()
  is PythonEnvironment.Conda,
  is PythonEnvironment.SystemPython,
  null,
    -> stdlibLibDirectory()
}

/**
 * The `site-packages/` directory inside this SDK's [libDirectory], or `null` when either the lib
 * directory or its `site-packages` child cannot be located.
 *
 * For a virtual env that was created with `--system-site-packages`, the venv's own `site-packages`
 * is returned (as opposed to the interpreter's), since that's the one `pip` writes new packages to.
 * Some system Python distributions (notably on Linux) ship without a `site-packages` directory at
 * all, in which case this returns `null`.
 */
@Internal
@RequiresBackgroundThread
fun PythonInterpreter.sitePackagesDirectory(): VirtualFile? = libDirectory()?.findChild(PyNames.SITE_PACKAGES)

/**
 * The interpreter's standard library directory, or `null` when none of this SDK's class roots
 * looks like one.
 *
 * Class roots are scanned for `__future__.py`/`__future__.pyc` plus `xml/` and `email/`; under
 * unit-test mode a folder named `Lib` also qualifies to support mock SDKs. Independent of
 * environment kind: for a [PythonEnvironment.Venv] this still returns the *base* interpreter's
 * stdlib (which is included in the venv's class roots), not the venv's own (mostly empty) lib.
 */
@Internal
@RequiresBackgroundThread
fun PythonInterpreter.stdlibLibDirectory(): VirtualFile? {
  for (file in sdkClassRoots) {
    if (!file.isValid) continue
    if ((file.findChild("__future__.py") != null || file.findChild("__future__.pyc") != null) &&
        file.findChild("xml") != null && file.findChild("email") != null) {
      return file
    }
    // Mock SDK does not have the aforementioned modules.
    if (ApplicationManager.getApplication().isUnitTestMode && file.name == "Lib") {
      return file
    }
  }
  return null
}

/**
 * The virtual environment's own `lib/pythonX.Y/` directory when this SDK's environment is a
 * [PythonEnvironment.Venv], or `null` otherwise (including when no environment was detected).
 *
 * Resolves [PythonEnvironment.Venv.libRoot] against the SDK's class roots first (covering both
 * direct matches and the `site-packages` shortcut, since the `venv` module doesn't add
 * `lib/pythonX.Y` itself to `sys.path`), with a [LocalFileSystem] fallback when the SDK has no
 * class roots yet (e.g. a fresh empty SDK created for package management).
 */
@Internal
@RequiresBackgroundThread
fun PythonInterpreter.venvLibDirectory(): VirtualFile? {
  val venv = pythonEnvironment as? PythonEnvironment.Venv ?: return null
  val libRoot = venv.libRoot
  val classRoots = sdkClassRoots
  // Empty in case of a temporary empty SDK created to install package management.
  if (classRoots.isEmpty()) {
    return LocalFileSystem.getInstance().findFileByNioFile(libRoot)
  }
  for (file in classRoots) {
    if (file.toNioPath() == libRoot) return file
    val parent = file.parent
    if (file.name == PyNames.SITE_PACKAGES && parent != null && parent.toNioPath() == libRoot) {
      return parent
    }
  }
  return null
}

private val PythonInterpreter.sdkClassRoots: Array<VirtualFile>
  get() = runReadActionBlocking { sdk.rootProvider.getFiles(OrderRootType.CLASSES) }
