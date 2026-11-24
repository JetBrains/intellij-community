// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.execution.configuration.AbstractRunConfiguration
import com.intellij.execution.target.RunConfigurationTargetEnvironmentAdjuster
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.sdk.backend.impl.VERSION_NUMBER_RE
import com.intellij.python.sdk.backend.impl.associationProblem
import com.intellij.python.sdk.backend.impl.buildItem
import com.intellij.python.sdk.backend.impl.buildUnavailableItem
import com.intellij.python.sdk.backend.impl.recordedPythonInfo
import com.intellij.python.sdk.backend.service.ActivatableEnvironmentService
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.util.PathMappingSettings
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.sdk.associatedModuleDir
import com.jetbrains.python.sdk.configureBuilderToRunPythonOnTarget
import com.jetbrains.python.sdk.createSkeletonsRootDirectory
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.isTargetBased
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.sdk.readOnly.PythonSdkReadOnlyProvider
import com.jetbrains.python.sdk.skeletonsPath
import com.jetbrains.python.sdk.targetAdditionalData
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists
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
@RequiresBackgroundThread
// Off until PY-93940.
@RequiresReadLockAbsence(generateAssertion = false)
fun PythonInterpreter.findToolExecutable(tool: PyTool, executableName: String = tool.packageName.name): Path? =
  when (this) {
    is NativePythonInterpreter -> {
      val osFamily = pythonBinary.getEelDescriptor().osFamily
      pythonBinary.resolveSibling(osFamily.executableName(executableName)).takeIf { it.isExecutable() }
    }
    is TargetPythonInterpreter -> null
  }

/**
 * The [Sdk] behind this interpreter, only for the deprecated APIs that external plugins still call:
 * `com.jetbrains.python.sdk.PythonSdkUtil.findPythonSdk`, `Project.pythonSdk` and `PythonRunParams.getSdk`. They
 * return an `Sdk` by contract.
 *
 * Do not add a caller. Every other code uses the extensions of this file, and only this module reads the SDK.
 */
@ApiStatus.Internal
@Deprecated("Only for the deprecated APIs that return an Sdk to external plugins. Use PythonInterpreter extensions instead.")
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
 * gave: a probe that cannot reach its target leaves the recorded one alone rather than overwriting it, see
 * [PythonInterpreterProbe]. So a Docker or SSH interpreter still states its version while its host is down.
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
 * The dependency file stored for this interpreter in its [project][PythonInterpreter.project], such as its `requirements.txt` or its
 * `pyproject.toml`, or `null` when none is stored. The default of the package manager applies then. The file is
 * relative to [PythonInterpreter.ownerDir] when it is inside it.
 */
@get:Internal
val PythonInterpreter.requirementsPath: Path?
  get() = PythonInterpreterDetails.getInstance(project).requirementsPath(this)

/** Stores [path] as the dependency file, see [requirementsPath]. A relative [path] is relative to [PythonInterpreter.ownerDir]. `null` removes it. */
@Internal
suspend fun PythonInterpreter.writeRequirementsPath(path: Path?) {
  PythonInterpreterDetails.getInstance(project).writeRequirementsPath(this, path)
}

/**
 * The path mappings of the target of this interpreter, from a local root to a root on the target, or `null` for an
 * interpreter that has none, such as a native one.
 */
@get:Internal
val PythonInterpreter.pathMappings: PathMappingSettings?
  get() = PythonInterpreterDetails.getInstance(project).pathMappings(this)

/** Stores [mappings] as the path mappings of the target of this interpreter, see [pathMappings]. */
@Internal
suspend fun PythonInterpreter.writePathMappings(mappings: PathMappingSettings) {
  PythonInterpreterDetails.getInstance(project).writePathMappings(this, mappings)
}

/**
 * The language level of this interpreter, from its version, see [PythonInterpreterDetails.version]. The default
 * level when no version is recorded. It never starts the interpreter, so resolve and the language level pusher can
 * read it.
 */
@get:Internal
val PythonInterpreter.languageLevel: LanguageLevel
  get() = PythonInterpreterDetails.getInstance(project).version(this)?.let { LanguageLevel.getLanguageLevelFromVersionStringStaticSafe(it) }
          ?: LanguageLevel.getDefault()

/**
 * The class roots of this interpreter: the directories its `sys.path` holds, the skeletons and the bundled stubs,
 * see [PythonInterpreterRoots.classRoots].
 */
@get:Internal
val PythonInterpreter.classRoots: Array<VirtualFile>
  get() = PythonInterpreterRoots.getInstance(project).classRoots(this).toTypedArray()

/**
 * The standard library directory of this interpreter, or `null` when none of its class roots looks like one.
 *
 * Class roots are scanned for `__future__.py`/`__future__.pyc` plus `xml/` and `email/`; under unit-test mode a folder
 * named `Lib` also qualifies to support mock SDKs. Independent of environment kind: for a virtual environment this
 * still returns the *base* interpreter's stdlib (which is included in the venv's class roots), not the venv's own
 * (mostly empty) lib.
 */
@Internal
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
fun PythonInterpreter.stdlibLibDirectory(): VirtualFile? = findStdlibIn(classRoots)

/**
 * The `site-packages/` directory of this interpreter: inside the library directory of its environment, else inside
 * its standard library. `null` when either the directory or its `site-packages` child cannot be located.
 *
 * For a virtual env that was created with `--system-site-packages`, the venv's own `site-packages` is returned (as
 * opposed to the interpreter's), since that's the one `pip` writes new packages to. Some system Python distributions
 * (notably on Linux) ship without a `site-packages` directory at all, in which case this returns `null`.
 */
@Internal
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
fun PythonInterpreter.sitePackagesDirectory(): VirtualFile? {
  val libDirectory = if (venvLibRoot() != null) venvLibDirectory() else stdlibLibDirectory()
  return libDirectory?.findChild(PyNames.SITE_PACKAGES)
}

/**
 * The directory of the generated skeletons of this interpreter, or `null` when it has none: the class root named
 * `python_stubs`. A mock interpreter of a test keeps it next to its other roots, so this scans the roots.
 */
@Internal
fun PythonInterpreter.skeletonsDirectory(): VirtualFile? =
  classRoots.firstOrNull { it.isValid && it.path.contains(PythonSdkUtil.SKELETON_DIR_NAME) }

/**
 * The lifetime of this interpreter in its [project][PythonInterpreter.project], for a cache that holds something for it. It ends when the registry
 * removes this interpreter, when the registry replaces the environment behind its key, or when its project closes.
 * `null` when this interpreter has no lifetime of its own, so it lives as long as the caller.
 */
@get:Internal
val PythonInterpreter.lifetime: Disposable?
  get() = sdk as? Disposable

/**
 * This interpreter as a UI list holds it.
 *
 * Reads only what the interpreter and its SDK already record, including whether it can be used — so the `[invalid]`
 * marker costs nothing and this needs no coroutine. Getting the [PythonInterpreter] in the first place is the part
 * that must happen off the EDT.
 */
fun PythonInterpreter.asItem(): PyInterpreterItem = buildItem()

/**
 * The row of the interpreter of this key, which an interpreter library entry names while its environment is gone. It is
 * `[invalid]`, and its reason names the environment and its manager. It reads no file and runs nothing.
 */
@Internal
fun PythonInterpreterKey.asUnavailableItem(): PyInterpreterItem = buildUnavailableItem()

/** The flavor this interpreter was set up with. */
val PythonInterpreter.flavor: PythonSdkFlavor<*>
  get() = sdk.pySdkAdditionalData.flavor

/**
 * Saves [TargetPythonInterpreter.targetConfiguration] after the user edited it in place, where the flow of this session
 * keeps it: the target of the project in the interpreter library flow, see [replaceTarget], and the SDK data in the
 * legacy flow. Then it requests an update of this interpreter.
 */
@Internal
@RequiresEdt
fun TargetPythonInterpreter.saveEditedTargetConfiguration() {
  val data = sdk.targetAdditionalData
  // The SDK data holds the edited configuration too: it keeps a serialized copy, which the legacy flow stores.
  data?.notifyTargetEnvironmentConfigurationChanged()
  if (isInterpreterLibraryFlowEnabled()) {
    replaceTarget(project, targetConfiguration)
  }
  else if (data != null) {
    val modificator = sdk.sdkModificator
    modificator.sdkAdditionalData = data
    ApplicationManager.getApplication().runWriteAction { modificator.commitChanges() }
  }
  PythonInterpreterUpdater.getInstance(project).request(this, PythonInterpreterUpdateRequest(packages = true, background = false))
}

/** The target data of this interpreter, or `null` for an interpreter that runs on no target. */
val PythonInterpreter.targetAdditionalData: PyTargetAwareAdditionalData?
  get() = sdk.targetAdditionalData

/** Whether this interpreter runs on a target, such as WSL, SSH or Docker. */
val PythonInterpreter.isTargetBased: Boolean
  get() = sdk.isTargetBased()

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
/** The library root of the environment of a native interpreter, or `null` for a target or an environment without one. */
internal fun PythonInterpreter.venvLibRoot(): Path? = when (this) {
  is NativePythonInterpreter -> pythonEnvironment?.libRoot
  is TargetPythonInterpreter -> null
}

/**
 * [PythonInterpreter.stdlibLibDirectory] for an SDK that has no interpreter yet, such as an SDK without an owner.
 *
 * The scan reads only the class roots of the SDK, so it needs no interpreter.
 */
@RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
fun Sdk.stdlibLibDirectory(): VirtualFile? = findStdlibIn(PythonSdkUtil.getPythonClassRoots(this))

internal fun findStdlibIn(classRoots: Array<VirtualFile>): VirtualFile? {
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

/** The root of the binary skeletons of this interpreter, on the disk of the IDE, or `null` when it has no home path. */
@get:Internal
val PythonInterpreter.skeletonsPath: Path?
  get() = sdk.skeletonsPath

/**
 * Makes the skeletons root of this interpreter when it is not there, see [skeletonsPath]. A failure is logged: the
 * skeletons are optional, and their generation reports its own failure.
 */
@Internal
@RequiresBackgroundThread(generateAssertion = false)
@RequiresReadLockAbsence
fun PythonInterpreter.ensureSkeletonsRoot() {
  val path = skeletonsPath ?: return
  if (path.exists()) return
  try {
    sdk.createSkeletonsRootDirectory()
  }
  catch (e: IOException) {
    logger<PythonInterpreter>().warn("Failed to create skeletons directory $path", e)
  }
}

/**
 * Whether the SDK of this interpreter is disposed. The registry can still hold such an interpreter for a moment after
 * the SDK is removed, and its roots and version are then gone.
 */
val PythonInterpreter.isDisposed: Boolean
  get() = PythonSdkUtil.isDisposed(sdk)

/**
 * The directory that holds the sources copied from the target of this interpreter, one subdirectory for each remote
 * root, or `null` when no sources are copied yet.
 */
fun TargetPythonInterpreter.remoteSourcesDirectory(): VirtualFile? {
  val path = remoteSourcesLocalPath ?: return null
  return VirtualFileManager.getInstance().findFileByNioPath(path)
}

/**
 * Where the sources copied from the target of this interpreter are stored on this machine, or `null` for an interpreter
 * without target data. The sync of the sources copies them there, see [remoteSourcesDirectory].
 */
@get:Internal
val TargetPythonInterpreter.remoteSourcesLocalPath: Path?
  get() = targetAdditionalData?.let(::remoteSourcesLocalPath)

/**
 * The request that uploads the PyCharm helpers to the target of this interpreter, or `null` when no loaded plugin
 * supports the target, see [PythonInterpreterTargetEnvironmentFactory].
 */
@Internal
fun TargetPythonInterpreter.targetEnvironmentRequestOrNull(): HelpersAwareTargetEnvironmentRequest? =
  targetAdditionalData?.let { PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(it, project) }

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
fun PythonInterpreter.targetEnvironmentRequest(): HelpersAwareTargetEnvironmentRequest =
  PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, project)

/**
 * The name of the SDK of the table that this interpreter wraps, for the `SDK_NAME` field that an older build reads to
 * find the SDK. Only the legacy flow writes that field, see [isInterpreterLibraryFlowEnabled].
 */
@get:Internal
val PythonInterpreter.legacyTableSdkName: @NlsSafe String
  get() = sdk.name

/** The home path of the SDK of the table that this interpreter wraps, for the `SDK_HOME` field, see [legacyTableSdkName]. */
@get:Internal
val PythonInterpreter.legacyTableSdkHome: String?
  get() = sdk.homePath

/**
 * The adjuster that the platform gives for the target of this interpreter, such as Docker or SSH, or `null` when none
 * applies. The platform API takes an [Sdk], so this is the one place that hands it one.
 */
@Internal
fun PythonInterpreter.runConfigurationTargetAdjuster(): RunConfigurationTargetEnvironmentAdjuster? =
  RunConfigurationTargetEnvironmentAdjuster.Factory.findTargetEnvironmentRequestAdjuster(sdk)

/**
 * The run configuration UI of this adjuster for [runConfiguration], which reads the interpreter with [interpreter] each
 * time. The platform UI reads an SDK, so this is where the interpreter gives it.
 */
@Internal
fun RunConfigurationTargetEnvironmentAdjuster.createRunConfigurationUI(
  runConfiguration: AbstractRunConfiguration,
  interpreter: () -> PythonInterpreter?,
): SettingsEditor<AbstractRunConfiguration>? = createAdditionalRunConfigurationUI(runConfiguration) { interpreter()?.sdk }

/**
 * Sets the Python binary of this interpreter, and what its flavor needs, such as a conda activation, on
 * [targetCommandLineBuilder].
 */
@Internal
@RequiresBackgroundThread
fun PythonInterpreter.configureBuilderToRunPythonOnTarget(targetCommandLineBuilder: TargetedCommandLineBuilder) {
  sdk.configureBuilderToRunPythonOnTarget(targetCommandLineBuilder)
}

/**
 * The variables that the activation of the environment of this interpreter sets, such as `VIRTUAL_ENV`. A target
 * interpreter has no detected environment, so it has none.
 */
@Internal
suspend fun PythonInterpreter.activationEnvironment(): PyResult<Map<String, String>> = when (this) {
  is NativePythonInterpreter -> service<ActivatableEnvironmentService>().activationEnvironment(pythonBinary)
  is TargetPythonInterpreter -> PyResult.success(emptyMap())
}

/**
 * [activationEnvironment] for a Java caller that cannot suspend. Call it off the EDT. The assertion is off, as for the
 * SDK variant before it: a run configuration test builds its command line on the EDT.
 */
@Internal
@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun PythonInterpreter.activationEnvironmentBlocking(): PyResult<Map<String, String>> =
  runBlockingMaybeCancellable { activationEnvironment() }

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

/** Whether packages cannot be installed into this interpreter, for example one inside a Docker image. */
@get:Internal
val PythonInterpreter.isReadOnly: Boolean
  get() = PythonSdkReadOnlyProvider.isReadOnly(this)

/** Why packages cannot be installed into this interpreter. See [isReadOnly]. */
@get:Internal
val PythonInterpreter.readOnlyErrorMessage: @Nls String
  get() = PythonSdkReadOnlyProvider.getReadOnlyMessage(this) ?: PySdkBundle.message("python.sdk.read.only", sdk.name)
