// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.pytools.PyTool
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyNames
import com.jetbrains.python.sdk.pyInterpreterPresentation
import java.nio.file.Path
import kotlin.io.path.isExecutable

/** The tool's own executable name on [this] OS — Windows wants the `.exe`. */
private fun EelOsFamily.executableName(binaryName: String): String = when (this) {
  EelOsFamily.Posix -> binaryName
  EelOsFamily.Windows -> "$binaryName.exe"
}

/**
 * [tool]'s executable installed *into* this interpreter's environment — its scripts directory, beside the interpreter
 * binary — or `null` when the tool is not installed there.
 *
 * The receiver is the interpreter because that is what determines where to look: the answer is a property of this
 * environment, not of the tool. Only local interpreters are supported for now.
 */
fun PythonInterpreter.findToolExecutable(tool: PyTool, executableName: String = tool.packageName.name): Path? =
  pythonBinaryPath?.let { binary ->
    val osFamily = binary.getEelDescriptor().osFamily
    binary.resolveSibling(osFamily.executableName(executableName)).takeIf { it.isExecutable() }
  }

@Deprecated("try to avoid Sdk API usage, use PythonInterpreter extensions instead", ReplaceWith("PythonInterpreterExt.kt"))
fun PythonInterpreter.getSdkAPI(): Sdk = sdk

val PythonInterpreter.presentation: PythonInterpreterPresentation
  get() = sdk.pyInterpreterPresentation()

/**
 * The Python `lib/` directory backing this SDK, or `null` when it cannot be located.
 *
 * An environment with a library directory of its own answers with it, through
 * [venvLibDirectory]. Every other environment, and an unknown one, answers with the interpreter's
 * standard library directory, through [stdlibLibDirectory].
 */
@RequiresBackgroundThread
private fun PythonInterpreter.libDirectory(): VirtualFile? =
  if (pythonEnvironment?.libRoot != null) venvLibDirectory() else stdlibLibDirectory()

/**
 * The `site-packages/` directory inside this SDK's [libDirectory], or `null` when either the lib
 * directory or its `site-packages` child cannot be located.
 *
 * For a virtual env that was created with `--system-site-packages`, the venv's own `site-packages`
 * is returned (as opposed to the interpreter's), since that's the one `pip` writes new packages to.
 * Some system Python distributions (notably on Linux) ship without a `site-packages` directory at
 * all, in which case this returns `null`.
 */
@RequiresBackgroundThread
fun PythonInterpreter.sitePackagesDirectory(): VirtualFile? = libDirectory()?.findChild(PyNames.SITE_PACKAGES)

/**
 * The interpreter's standard library directory, or `null` when none of this SDK's class roots
 * looks like one.
 *
 * Class roots are scanned for `__future__.py`/`__future__.pyc` plus `xml/` and `email/`; under
 * unit-test mode a folder named `Lib` also qualifies to support mock SDKs. Independent of
 * environment kind: for a virtual environment this still returns the *base* interpreter's
 * stdlib (which is included in the venv's class roots), not the venv's own (mostly empty) lib.
 */
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
 * The environment's own `lib/pythonX.Y/` directory when this SDK's environment has one,
 * or `null` otherwise (including when no environment was detected).
 *
 * Resolves [PythonEnvironment.libRoot] against the SDK's class roots first (covering both
 * direct matches and the `site-packages` shortcut, since the `venv` module doesn't add
 * `lib/pythonX.Y` itself to `sys.path`), with a [LocalFileSystem] fallback when the SDK has no
 * class roots yet (e.g. a fresh empty SDK created for package management).
 */
@RequiresBackgroundThread
fun PythonInterpreter.venvLibDirectory(): VirtualFile? {
  val libRoot = pythonEnvironment?.libRoot ?: return null
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
