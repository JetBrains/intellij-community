// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.resolveEelMachine
import com.intellij.platform.util.annotations.paths.OsPath
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.activationEnvironmentBlocking
import com.jetbrains.python.sdk.configureBuilderToRunPythonOnTarget
import com.jetbrains.python.sdk.impl.asBinToExecuteImpl
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.remoteInterpreterLocalRoots
import com.jetbrains.python.sdk.skeletonsPath
import com.jetbrains.python.sdk.targetAdditionalData
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.PyTargetAwareAdditionalData.Companion.pathsAddedByUser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * A Python environment on a disk: the binary that runs it. Interpreters of several projects can run one environment,
 * such as one virtual env that two projects use.
 *
 * A native environment is its binary on an eel machine. Two paths of one machine, such as `\\wsl$\Ubuntu` and
 * `\\wsl.localhost\Ubuntu`, name one environment. A link is kept as it is: the binary of a virtual env links to its
 * base interpreter, and the two are two environments. A target environment is its binary on a target configuration.
 */
@ApiStatus.Internal
sealed interface PythonEnvironmentKey {
  /** The path of the binary of the environment on its machine. */
  val path: String

  /**
   * A binary at [path] on the eel machine [machine]. The path is the one inside the machine, and no [EelPath]: an eel
   * path keeps its descriptor, and two descriptors of one machine name one environment.
   */
  data class Native(val machine: String, override val path: @OsPath String) : PythonEnvironmentKey

  /** A binary at [path] on the target configuration [targetId]. */
  data class Target(val targetId: String, override val path: FullPathOnTarget) : PythonEnvironmentKey

  companion object {
    /**
     * The key of the environment of [interpreter]: the Eel machine and the path of its binary, or its target and the path
     * on the target. `null` when the Eel machine of a native interpreter is not available.
     */
    suspend fun of(interpreter: PythonInterpreter): PythonEnvironmentKey? = when (interpreter) {
      is NativePythonInterpreter -> nativeOf(interpreter.pythonBinary.normalize())
      is TargetPythonInterpreter -> Target(interpreter.targetId, interpreter.pythonBinary.pathString)
    }

    /** The key of the environment of [sdk], or `null` when [sdk] names no binary. */
    suspend fun of(sdk: Sdk): PythonEnvironmentKey? {
      val homePath = sdk.homePath ?: return null
      return when (val data = sdk.sdkAdditionalData) {
        is PyTargetAwareAdditionalData -> data.targetEnvironmentConfiguration?.let { Target(it.uuid, homePath) }
        is PythonSdkAdditionalData -> nativeOf(homePath)
        else -> null
      }
    }

    private suspend fun nativeOf(homePath: @OsPath String): Native? = binaryOf(homePath)?.let { nativeOf(it) }

    private suspend fun nativeOf(binary: Path): Native? {
      val machine = try {
        binary.getEelDescriptor().resolveEelMachine().internalName
      }
      catch (_: EelUnavailableException) {
        return null
      }
      return Native(machine, binary.asEelPath().toString())
    }

    private fun binaryOf(homePath: @OsPath String): Path? =
      try {
        Path.of(homePath).normalize()
      }
      catch (_: InvalidPathException) {
        null
      }
  }
}

/** What an environment says about itself, see [PythonEnvironments]. A `null` fact is one that no update read. */
@ApiStatus.Internal
data class PythonEnvironmentFacts(
  /** The version string, such as `Python 3.12.1`. */
  val version: String? = null,
  /**
   * The local paths of `sys.path`, in its order. For a target, the local copies of its remote paths, which come with its
   * skeletons, see [PythonEnvironmentStep.awaited].
   */
  val sysPath: List<String>? = null,
  /** Why an awaited step could not read the environment, or `null`, such as a probe of a binary that does not run. */
  val awaitedError: PyError? = null,
  /**
   * Why a background step failed, or `null`, such as a skeleton generation of a binary that does not run. It is an
   * exception, because the project that tells the user picks the message by its type.
   */
  val backgroundError: Exception? = null,
)

/**
 * What a step of an environment update gets, see [PythonEnvironmentStep]. The backend builds it from an interpreter that
 * runs the environment, see [PythonEnvironments.contextOf]. Several projects can run one environment: the context comes
 * from the interpreter of the project that asked first.
 */
@ApiStatus.Internal
class PythonEnvironmentContext internal constructor(
  val key: PythonEnvironmentKey,
  /** The binary of the environment, for `ExecService`. */
  val binary: BinaryToExec,
  /** The directory of the generated skeletons of the environment, or `null` when the environment has none. */
  val skeletonsDirectory: Path?,
  /**
   * The local roots of the path mappings of a remote environment, or `null` for an environment that is not remote. The
   * skeleton generation copies the remote `sys.path` there.
   */
  val remoteRoots: List<String>?,
  /** The directory of the local copies of the remote sources of a target, or `null` for an environment on no target. */
  val remoteSourcesDirectory: Path?,
  /** The paths that the user added to the interpreter on its target. */
  val targetUserPaths: List<String>,
  private val targetRequest: () -> HelpersAwareTargetEnvironmentRequest,
  private val pythonOnTarget: (TargetedCommandLineBuilder) -> Unit,
  private val activation: () -> Map<String, String>?,
) {
  /**
   * A new request for the machine of the environment, which uploads the PyCharm helpers there. The target of a target
   * environment comes from the project that asked first.
   */
  fun newTargetRequest(): HelpersAwareTargetEnvironmentRequest = targetRequest()

  /** Sets the Python binary of the environment, and what its flavor needs, such as a conda activation, on [builder]. */
  @RequiresBackgroundThread
  fun configureBuilderToRunPython(builder: TargetedCommandLineBuilder): Unit = pythonOnTarget(builder)

  /** The variables that the activation of the environment sets, such as `VIRTUAL_ENV`, or `null` when the read failed. */
  @RequiresBackgroundThread
  fun activationEnvironmentBlocking(): Map<String, String>? = activation()

  override fun toString(): String = "PythonEnvironmentContext($key)"
}

/** The context of the environment of [interpreter] with the [key] and the [binary] of that environment. */
private fun environmentContextOf(key: PythonEnvironmentKey, interpreter: PythonInterpreter, binary: BinaryToExec): PythonEnvironmentContext {
  val sdk = interpreter.sdk
  val targetData = sdk.targetAdditionalData
  return PythonEnvironmentContext(
    key = key,
    binary = binary,
    skeletonsDirectory = sdk.skeletonsPath,
    remoteRoots = if (PythonSdkUtil.isRemote(sdk)) sdk.remoteInterpreterLocalRoots else null,
    remoteSourcesDirectory = targetData?.let(::remoteSourcesLocalPath),
    targetUserPaths = targetData?.pathsAddedByUser?.map { it.value }.orEmpty(),
    targetRequest = { PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(sdk, interpreter.project) },
    pythonOnTarget = { sdk.configureBuilderToRunPythonOnTarget(it) },
    activation = { sdk.activationEnvironmentBlocking().successOrNull },
  )
}

/**
 * One step of an environment update. The steps run in their order: the awaited steps first, then the others, see
 * [PythonEnvironments].
 */
@ApiStatus.Internal
interface PythonEnvironmentStep {
  /**
   * Whether a caller of [PythonEnvironments.update] waits for this step, such as the probe of the version. The others,
   * such as the skeleton generation, run after the awaited steps, see [PythonEnvironments.updateInBackground].
   */
  val awaited: Boolean get() = false

  /**
   * Runs this step for the environment of [context], and returns [facts] with what it read. A step keeps its failure in
   * the facts, see [PythonEnvironmentFacts.awaitedError] and [PythonEnvironmentFacts.backgroundError], and the later
   * steps still run.
   */
  suspend fun update(context: PythonEnvironmentContext, facts: PythonEnvironmentFacts): PythonEnvironmentFacts

  companion object {
    val EP_NAME: ExtensionPointName<PythonEnvironmentStep> = ExtensionPointName("Pythonid.environmentUpdateStep")
  }
}

/**
 * The updates of the Python environments of the application, see [PythonEnvironmentKey]. An update runs each
 * [PythonEnvironmentStep] once for an environment, for every project that runs it.
 *
 * The awaited steps and the others run as two updates, each under its own rule: a request returns the facts of an
 * update that started after the request. Requests during an update wait for it and then share one more, because the
 * update read the environment as it was when it started. An update of the steps that are not awaited runs after an
 * update of the awaited steps that covers it, so it sees fresh facts. At most [MAX_UPDATES] updates run at a time.
 * `ExecService` limits the processes of the application too.
 */
@Service(Service.Level.APP)
@ApiStatus.Internal
class PythonEnvironments {
  /** The runs of one group of steps of one environment: a request waits for a run that started after it. */
  private class Runs {
    val mutex = Mutex()

    /** The number of requests so far, and the number of requests that the last run covered. */
    private var requested = 0L
    private var done = 0L

    /** Runs [run] unless a run that started after this call covers it. */
    suspend fun runOrJoin(run: suspend () -> Unit) {
      val ticket = synchronized(this) { ++requested }
      mutex.withLock {
        if (synchronized(this) { done >= ticket }) return
        val covered = synchronized(this) { requested }
        run()
        synchronized(this) { done = covered }
      }
    }
  }

  private class Environment {
    val awaited = Runs()
    val background = Runs()

    /** The facts of the last update, see [PythonEnvironments.factsIfReady]. */
    @Volatile
    var facts: PythonEnvironmentFacts = PythonEnvironmentFacts()
  }

  private val environments = ConcurrentHashMap<PythonEnvironmentKey, Environment>()
  private val permits = Semaphore(MAX_UPDATES)

  /**
   * Runs the awaited steps for the environment of [interpreter], unless an update that started after this call covers
   * them, and returns its facts. The update runs with the binary of [interpreter]: any interpreter of the environment
   * runs the same binary.
   */
  suspend fun update(interpreter: PythonInterpreter): PyResult<PythonEnvironmentFacts> =
    run(interpreter) { context, environment -> environment.awaited.runOrJoin { runSteps(context, environment, awaited = true) } }

  /**
   * Runs the steps that are not awaited for the environment of [interpreter], such as the skeleton generation, after an
   * update of the awaited steps, and returns its facts. Like [update], it joins a run that started after this call.
   */
  suspend fun updateInBackground(interpreter: PythonInterpreter): PyResult<PythonEnvironmentFacts> =
    run(interpreter) { context, environment ->
      environment.awaited.runOrJoin { runSteps(context, environment, awaited = true) }
      environment.background.runOrJoin { runSteps(context, environment, awaited = false) }
    }

  /** The facts of the last update of the environment [key], or `null` when none ran. It does not wait. */
  fun factsIfReady(key: PythonEnvironmentKey): PythonEnvironmentFacts? = environments[key]?.facts

  /**
   * The context of the environment of [interpreter], for a caller that runs a step of its own, such as the generation of
   * the skeletons of one module. A failure when [interpreter] names no environment or no binary.
   */
  suspend fun contextOf(interpreter: PythonInterpreter): PyResult<PythonEnvironmentContext> {
    val key = PythonEnvironmentKey.of(interpreter) ?: return brokenConfiguration(interpreter)
    val binary = interpreter.sdk.asBinToExecuteImpl().getOr { return PyResult.failure(it.error) }
    return PyResult.success(environmentContextOf(key, interpreter, binary))
  }

  private suspend fun run(
    interpreter: PythonInterpreter,
    body: suspend (PythonEnvironmentContext, Environment) -> Unit,
  ): PyResult<PythonEnvironmentFacts> {
    val key = PythonEnvironmentKey.of(interpreter) ?: return brokenConfiguration(interpreter)
    val environment = environments.computeIfAbsent(key) { Environment() }
    val binary = interpreter.sdk.asBinToExecuteImpl().getOr { failure ->
      LOG.warn("No update of $key: ${failure.error.message}")
      return PyResult.success(environment.facts)
    }
    body(environmentContextOf(key, interpreter, binary), environment)
    return PyResult.success(environment.facts)
  }

  private suspend fun runSteps(context: PythonEnvironmentContext, environment: Environment, awaited: Boolean) {
    val steps = PythonEnvironmentStep.EP_NAME.extensionList.filter { it.awaited == awaited }
    if (steps.isEmpty()) return
    // The steps read the environment for the IDE, and for no command that a user ran, so their processes are background
    // ones, also when a dialog waits for the facts.
    withContext(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) {
      permits.withPermit {
        // An update of the awaited steps starts with no errors, and keeps the facts that the other steps read before.
        var facts = environment.facts.let { if (awaited) it.copy(awaitedError = null) else it.copy(backgroundError = null) }
        for (step in steps) facts = step.update(context, facts)
        environment.facts = facts
      }
    }
  }

  private fun <T> brokenConfiguration(interpreter: PythonInterpreter): PyResult<T> =
    PyResult.localizedError(PySdkBundle.message("python.sdk.broken.configuration", interpreter.sdk.name))

  companion object {
    private val LOG = logger<PythonEnvironments>()
    private const val MAX_UPDATES = 2

    @JvmStatic
    fun getInstance(): PythonEnvironments = service()
  }
}
