// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.sdk.backend.impl.VERSION_NUMBER_RE
import com.intellij.python.sdk.backend.impl.associationProblem
import com.intellij.python.sdk.backend.impl.buildItem
import com.intellij.python.sdk.backend.impl.recordedPythonInfo
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.sdk.associatedModuleDir
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.isTargetBased
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.sdk.targetAdditionalData
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString

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
 * environment, not of the tool. Only a native interpreter is supported: `null` for a target.
 */
fun PythonInterpreter.findToolExecutable(tool: PyTool, executableName: String = tool.packageName.name): Path? =
  when (this) {
    is NativePythonInterpreter -> {
      val osFamily = pythonBinary.getEelDescriptor().osFamily
      pythonBinary.resolveSibling(osFamily.executableName(executableName)).takeIf { it.isExecutable() }
    }
    is TargetPythonInterpreter -> null
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
  val environment = when (this) {
    is NativePythonInterpreter -> environmentResult.getOr { return it }
    // A target interpreter has no detected environment: its version is the one the SDK records.
    is TargetPythonInterpreter -> null
  }
  associationProblem()?.let { return it }

  // The environment's own version first, since it is exact; then the SDK's, which carries a `Python ` prefix.
  val recorded = environment?.version ?: sdk.versionString?.let { VERSION_NUMBER_RE.find(it)?.value }
  return recordedPythonInfo(recorded)?.let { PyResult.success(it) }
         ?: PyResult.localizedError(PySdkBundle.message("python.sdk.version.not.recorded", sdk.name))
}

/**
 * This interpreter as a UI list holds it.
 *
 * Reads only what the interpreter and its SDK already record, including whether it can be used — so the `[invalid]`
 * marker costs nothing and this needs no coroutine. Getting the [PythonInterpreter] in the first place is the part
 * that must happen off the EDT.
 */
fun PythonInterpreter.asItem(): PyInterpreterItem = buildItem()

/**
 * Every SDK as a UI list holds it, resolved off the EDT, in order.
 *
 * This is how a list of interpreters is built. The work is detecting each environment, which is local file reads that
 * [Sdk.pythonInterpreterAsync] caches per SDK — so the first list pays for it and later ones do not.
 */
suspend fun Iterable<Sdk>.pyInterpreterItems(): List<PyInterpreterItem> = mapNotNull { it.interpreterItem() }

/**
 * This SDK as a UI list holds it, or `null` for a broken SDK that has no ref: no row stands for it. For a caller
 * that the platform hands an [Sdk], such as the status bar or a run configuration.
 */
suspend fun Sdk.interpreterItem(): PyInterpreterItem? = pythonInterpreterResultAsync().successOrNull?.asItem()

/**
 * Whether this SDK's interpreter can be used, for a Java caller that cannot suspend.
 *
 * From Kotlin call [getPythonInfo] instead, which also says why it cannot be used.
 */
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
@RequiresBlockingContext
fun Sdk.isInterpreterUsable(): Boolean =
  runBlockingMaybeCancellable { pythonInterpreterResultAsync().successOrNull?.getPythonInfo() } is Result.Success<*>

/**
 * The SDK this item names, or `null` when no SDK has its key any more.
 *
 * A list is built once and applied later, so the interpreter it named can be removed in between. `null` is that case,
 * and the caller decides what to tell the user. It does not wait: it reads the SDK table, because a Java combo box
 * calls it on the EDT.
 */
fun PyInterpreterItem.findSdk(): Sdk? = PythonSdkUtil.getAllSdks().firstOrNull { interpreterRefOf(it) == ref }

/** The flavor this interpreter was set up with. */
val PythonInterpreter.flavor: PythonSdkFlavor<*>
  get() = sdk.pySdkAdditionalData.flavor

/** The target data of this interpreter, or `null` for an interpreter that runs on no target. */
val PythonInterpreter.targetAdditionalData: PyTargetAwareAdditionalData?
  get() = sdk.targetAdditionalData

/** Whether this interpreter runs on a target, such as WSL, SSH or Docker. */
val PythonInterpreter.isTargetBased: Boolean
  get() = sdk.isTargetBased()

/** The requirements file path stored for this interpreter, or `null` when none is stored. */
val PythonInterpreter.requirementsPath: Path?
  get() = sdk.pySdkAdditionalData.requirementsPath

/** The paths the user added to the `sys.path` of this interpreter in the interpreter settings. */
val PythonInterpreter.addedPathFiles: Set<VirtualFile>
  get() = sdk.pySdkAdditionalData.addedPathFiles.orEmpty()

/** The directory of the module this interpreter was created for, or `null` when it records none. */
val PythonInterpreter.associatedModuleDir: VirtualFile?
  get() = sdk.associatedModuleDir

/**
 * Whether this interpreter wraps [sdk].
 *
 * For code that holds an [Sdk] from an API not migrated yet, such as a listener, and must find the interpreter it
 * names.
 */
fun PythonInterpreter.isFor(sdk: Sdk): Boolean = this.sdk == sdk

/**
 * The Python `lib/` directory backing this SDK, or `null` when it cannot be located.
 *
 * An environment with a library directory of its own answers with it, through
 * [venvLibDirectory]. Every other environment, and an unknown one, answers with the interpreter's
 * standard library directory, through [stdlibLibDirectory].
 */
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
private fun PythonInterpreter.libDirectory(project: Project): VirtualFile? =
  if (venvLibRoot() != null) venvLibDirectory() else stdlibLibDirectory(project)

/** The library root of the environment of a native interpreter, or `null` for a target or an environment without one. */
private fun PythonInterpreter.venvLibRoot(): Path? = when (this) {
  is NativePythonInterpreter -> pythonEnvironment?.libRoot
  is TargetPythonInterpreter -> null
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
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
fun PythonInterpreter.sitePackagesDirectory(project: Project): VirtualFile? = libDirectory(project)?.findChild(PyNames.SITE_PACKAGES)

/**
 * [sitePackagesDirectory] for a caller that has an SDK and no project. A base interpreter without a library directory
 * of its own then finds its standard library in the roots of the SDK.
 */
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
fun Sdk.sitePackagesDirectory(): VirtualFile? {
  val interpreter = pythonInterpreterResult().successOrNull
  val libDirectory = if (interpreter?.venvLibRoot() != null) interpreter.venvLibDirectory() else stdlibLibDirectory()
  return libDirectory?.findChild(PyNames.SITE_PACKAGES)
}

/**
 * The interpreter's standard library directory in [project], or `null` when none of its class roots looks like one.
 *
 * Class roots are scanned for `__future__.py`/`__future__.pyc` plus `xml/` and `email/`; under
 * unit-test mode a folder named `Lib` also qualifies to support mock SDKs. Independent of
 * environment kind: for a virtual environment this still returns the *base* interpreter's
 * stdlib (which is included in the venv's class roots), not the venv's own (mostly empty) lib.
 */
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
fun PythonInterpreter.stdlibLibDirectory(project: Project): VirtualFile? = findStdlibIn(classRoots(project))

/**
 * [PythonInterpreter.stdlibLibDirectory] for an SDK that has no interpreter yet, such as an SDK without an owner.
 *
 * The scan reads only the class roots of the SDK, so it needs no interpreter.
 */
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
fun Sdk.stdlibLibDirectory(): VirtualFile? = findStdlibIn(PythonSdkUtil.getPythonClassRoots(this))

private fun findStdlibIn(classRoots: Array<VirtualFile>): VirtualFile? {
  for (file in classRoots) {
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
 * The environment's own `lib/pythonX.Y/` directory when this interpreter's environment has one,
 * or `null` otherwise (including when no environment was detected, and when the directory does not exist).
 *
 * The detected environment knows the path, [PythonEnvironment.libRoot], so this needs no class roots.
 */
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
fun PythonInterpreter.venvLibDirectory(): VirtualFile? =
  venvLibRoot()?.let { VirtualFileManager.getInstance().findFileByNioPath(it) }

/**
 * The language level of this interpreter in [project], from its version, see [PythonInterpreterDetails.version]. The
 * default level when no version is recorded. It never starts the interpreter, so resolve and the language level pusher
 * can read it.
 */
fun PythonInterpreter.languageLevel(project: Project): LanguageLevel =
  PythonInterpreterDetails.getInstance(project).version(this)?.let { LanguageLevel.getLanguageLevelFromVersionStringStaticSafe(it) }
  ?: LanguageLevel.getDefault()

/**
 * Whether the SDK of this interpreter is disposed. The registry can still hold such an interpreter for a moment after
 * the SDK is removed, and its roots and version are then gone.
 */
val PythonInterpreter.isDisposed: Boolean
  get() = PythonSdkUtil.isDisposed(sdk)

/**
 * The class roots of this interpreter in [project]: the directories its `sys.path` holds, the skeletons and the bundled
 * stubs, see [PythonInterpreterDetails.classRoots].
 */
fun PythonInterpreter.classRoots(project: Project): Array<VirtualFile> =
  PythonInterpreterDetails.getInstance(project).classRoots(this).toTypedArray()

/**
 * The directory of the generated skeletons of this interpreter in [project], or `null` when it has none: the class root
 * named `python_stubs`. A mock interpreter of a test keeps it next to its other roots, so this scans the roots.
 */
fun PythonInterpreter.skeletonsDirectory(project: Project): VirtualFile? =
  classRoots(project).firstOrNull { it.isValid && it.path.contains(PythonSdkUtil.SKELETON_DIR_NAME) }

/**
 * The directory that holds the sources copied from the target of this interpreter, one subdirectory for each remote
 * root, or `null` when no sources are copied yet.
 */
fun TargetPythonInterpreter.remoteSourcesDirectory(): VirtualFile? {
  val data = targetAdditionalData ?: return null
  return VirtualFileManager.getInstance().findFileByNioPath(remoteSourcesLocalPath(data))
}

/**
 * Where the sources copied from the target of [data] are stored on this machine. The updater copies them there, and
 * [TargetPythonInterpreter.remoteSourcesDirectory] finds them there.
 */
@Internal
fun remoteSourcesLocalPath(data: PyTargetAwareAdditionalData): Path =
  Path.of(PathManager.getSystemPath(), PythonSdkUtil.REMOTE_SOURCES_DIR_NAME, data.uuid.toString().hashCode().toString())

/**
 * The request that uploads the PyCharm helpers to the machine this interpreter runs on.
 *
 * A local interpreter answers too: the request then describes the local machine.
 */
@Internal
fun PythonInterpreter.targetEnvironmentRequest(project: Project): HelpersAwareTargetEnvironmentRequest =
  PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, project)

/**
 * The path of the Python binary of this interpreter, as the command line runs it: a local path for a native interpreter,
 * and the path on the target for a target interpreter. It is known also when the environment is not detected.
 */
val PythonInterpreter.binaryPathString: String
  get() = when (this) {
    is NativePythonInterpreter -> pythonBinary.pathString
    is TargetPythonInterpreter -> pythonBinary.toStringOnRemoteMachine()
  }

/** Whether this interpreter runs on a target, so a run on it needs path mappings. For Java, which cannot use `when`. */
val PythonInterpreter.isOnTarget: Boolean
  get() = when (this) {
    is TargetPythonInterpreter -> true
    is NativePythonInterpreter -> false
  }
