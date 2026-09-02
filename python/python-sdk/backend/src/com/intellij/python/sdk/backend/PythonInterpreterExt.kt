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
import com.intellij.python.sdk.backend.impl.VERSION_NUMBER_RE
import com.intellij.python.sdk.backend.impl.associationProblem
import com.intellij.python.sdk.backend.impl.buildItem
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
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

/**
 * The [Sdk] behind this interpreter, for a call site that must reach an `Sdk` API.
 *
 * Deliberately deprecated: it is the only way out, and every use of it is a use of the `Sdk` API that should be a
 * `PythonInterpreter` extension instead. Never put an `Sdk` in a UI list — lists hold
 * [com.intellij.python.sdk.common.PyInterpreterItem].
 */
@Deprecated("try to avoid Sdk API usage, use PythonInterpreter extensions instead", ReplaceWith("PythonInterpreterExt.kt"))
fun PythonInterpreter.getSdkAPI(): Sdk = sdk

/**
 * What this interpreter is, from what is already recorded about it.
 *
 * Never runs the interpreter. A virtualenv states its version in `pyvenv.cfg` and a conda environment in its
 * `conda-meta` entry; everything else answers from the version its SDK recorded when it was set up. That matters most
 * for a remote interpreter: a project can hold many, and asking each one would mean a round trip per interpreter.
 *
 * A failure is the reason this interpreter cannot be used, and there are three: detection already failed, the SDK
 * records no project for an environment that needs one, or nothing records a version. The verdict belongs here rather
 * than beside the environment because an interpreter is an SDK *and* its environment — a sound environment reached
 * through a misconfigured SDK is still not something a project can use.
 *
 * No recorded version means the interpreter was never set up successfully, so it is a failure like the other two, and
 * not a report that the environment is unreachable right now. An interpreter that answered once keeps the version it
 * gave: a probe that cannot reach its target leaves the recorded one alone rather than overwriting it — see
 * `PythonSdkUpdater.updateSdkVersion`. So a Docker or SSH interpreter still states its version while its host is down.
 *
 * Nothing caches this: it reads a few fields, so computing it again is cheaper than remembering it.
 */
fun PythonInterpreter.getPythonInfo(): PyResult<PythonInfo> {
  val detection = environmentResult
  if (detection is Result.Failure) return detection
  associationProblem()?.let { return it }

  // The environment's own version first, since it is exact; then the SDK's, which carries a `Python ` prefix. A
  // recorded version says nothing about free threading, so that stays at its default.
  val recorded = pythonEnvironment?.version ?: sdk.versionString?.let { VERSION_NUMBER_RE.find(it)?.value }
  return recorded
           ?.let { LanguageLevel.fromPythonVersionSafe(it) }
           ?.let { PyResult.success(PythonInfo(languageLevel = it, version = recorded)) }
         ?: PyResult.localizedError(PySdkBundle.message("python.sdk.version.not.recorded", sdk.name))
}

/**
 * This interpreter as a UI list holds it.
 *
 * Reads only what the interpreter and its SDK already record, including whether it can be used — so the `[invalid]`
 * marker costs nothing and this needs no coroutine. Getting the [PythonInterpreter] in the first place is the part
 * that must happen off the EDT.
 *
 * @param customName label to show instead of the SDK name.
 */
fun PythonInterpreter.asItem(customName: String? = null): PyInterpreterItem = buildItem(customName)

/**
 * Every SDK as a UI list holds it, resolved off the EDT, in order.
 *
 * This is how a list of interpreters is built. The work is detecting each environment, which is local file reads that
 * [Sdk.pythonInterpreterAsync] caches per SDK — so the first list pays for it and later ones do not.
 */
suspend fun Iterable<Sdk>.pyInterpreterItems(): List<PyInterpreterItem> =
  map { it.pythonInterpreterAsync().asItem() }

/**
 * Whether this SDK's interpreter can be used, for a Java caller that cannot suspend.
 *
 * From Kotlin call [getPythonInfo] instead, which also says why it cannot be used.
 */
@RequiresBackgroundThread
@RequiresBlockingContext
fun Sdk.isInterpreterUsable(): Boolean =
  runBlockingMaybeCancellable { pythonInterpreterAsync().getPythonInfo() } is Result.Success

/**
 * The registered SDK this item names, or `null` when no SDK carries that name any more.
 *
 * A list is built once and applied later, so the interpreter it named can be renamed or removed in between. `null` is
 * that case, and the caller decides what to tell the user.
 */
fun PyInterpreterItem.findSdk(): Sdk? {
  val ref = ref as? PyInterpreterRef.ExistingSdk ?: return null
  return PythonSdkUtil.findSdkByKey(ref.sdkName)
}

/**
 * The ref an interpreter list row carries for this SDK.
 *
 * Use it to find the row that stands for an SDK, instead of resolving every row back to its SDK. A row compares by
 * its ref alone, so the comparison needs no SDK at all.
 */
fun Sdk.asInterpreterRef(): PyInterpreterRef = PyInterpreterRef.ExistingSdk(name)

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
