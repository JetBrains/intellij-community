// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.sdk.backend.PythonInterpreterUpdater.Companion.MAX_UPDATES
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/** What an update of an interpreter does, see [PythonInterpreterUpdater]. */
@ApiStatus.Internal
data class PythonInterpreterUpdateRequest @JvmOverloads constructor(
  /** The update reads the installed packages again. */
  val packages: Boolean,
  /** A background process asked for the update, and not a user. */
  val background: Boolean,
  /** The caller ran the awaited steps already, see [PythonInterpreterUpdater.update]: the queue runs only the others. */
  val awaitedStepsDone: Boolean = false,
) {
  /** One request of a user makes the merged request one of a user, and one that needs the awaited steps runs them. */
  fun mergedWith(next: PythonInterpreterUpdateRequest): PythonInterpreterUpdateRequest =
    PythonInterpreterUpdateRequest(packages || next.packages, background && next.background, awaitedStepsDone && next.awaitedStepsDone)
}

/** One step of an update of an interpreter. The steps run in their order, see [PythonInterpreterUpdater]. */
@ApiStatus.Internal
interface PythonInterpreterUpdateStep {
  /**
   * Whether a caller of [PythonInterpreterUpdater.update] waits for this step. An awaited step, such as the probe of the
   * facts, must come before every step that is not awaited.
   */
  val awaited: Boolean get() = false

  /**
   * Runs this step. Returns `false` to stop the update: the later steps do not run. A failure that the step cannot
   * handle goes to [failure], and the update goes on.
   */
  suspend fun update(project: Project, interpreter: PythonInterpreter, request: PythonInterpreterUpdateRequest, failure: (PyError) -> Unit): Boolean

  /**
   * Whether [PythonInterpreterUpdater.request] accepts [request] for the interpreter [key]. It runs on the thread of the
   * caller, before the interpreter is built, so it takes no lock and does not wait.
   */
  fun accepts(project: Project, key: PythonInterpreterKey, request: PythonInterpreterUpdateRequest): Boolean = true

  companion object {
    val EP_NAME: ExtensionPointName<PythonInterpreterUpdateStep> = ExtensionPointName("Pythonid.interpreterUpdateStep")
  }
}

/**
 * Updates the interpreters of a project: it runs each [PythonInterpreterUpdateStep] for an interpreter, and then
 * restarts the analysis of the project. The disposal of the SDK cancels its update.
 *
 * At most one update of an interpreter runs at a time. A request for an interpreter whose update runs waits, and the
 * requests that wait merge into one. At most [MAX_UPDATES] updates run at a time. `ExecService` limits the processes
 * of all the updates of the application.
 *
 * An SDK that has no [PythonInterpreterKey] gets no update.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PythonInterpreterUpdater(private val project: Project, private val coroutineScope: CoroutineScope) {
  /**
   * The interpreters whose update runs or waits. The value is the request that waits, or `null` while an update runs
   * and no request waits.
   */
  private val updates = MutableStateFlow<Map<PythonInterpreterKey, Waiting?>>(emptyMap())
  private val permits = Semaphore(MAX_UPDATES)

  /** A request that waits. */
  private class Waiting(val sdk: Sdk, val request: PythonInterpreterUpdateRequest)

  /** Requests an update of [interpreter], see [request]. */
  fun request(interpreter: PythonInterpreter, request: PythonInterpreterUpdateRequest): Boolean =
    request(interpreter.sdk, request)

  /**
   * Requests an update of the interpreter of [sdk]. The update builds the interpreter. Returns whether the request was
   * accepted: the project and the SDK are there, and each step accepts it, see [PythonInterpreterUpdateStep.accepts].
   * An accepted request does nothing in a unit test, unless the test enabled the updates, see [setEnabledInTests].
   */
  fun request(sdk: Sdk, request: PythonInterpreterUpdateRequest): Boolean {
    if (project.isDisposed || (sdk is Disposable && Disposer.isDisposed(sdk))) return false
    val key = updateKeyOf(sdk) ?: return false
    if (PythonInterpreterUpdateStep.EP_NAME.extensionList.any { !it.accepts(project, key, request) }) return false
    if (!enabledInTests && ApplicationManager.getApplication().isUnitTestMode) {
      LOG.info("No update of ${sdk.name} in a unit test")
      return true
    }
    var start = false
    updates.update { map ->
      start = key !in map
      map + (key to Waiting(sdk, map[key]?.request?.mergedWith(request) ?: request))
    }
    // The queue runs for no user: its processes are background ones.
    if (start) coroutineScope.launch(Dispatchers.IO + NON_INTERACTIVE_ROOT_TRACE_CONTEXT) { runUpdates(key) }
    return true
  }

  /**
   * Updates [interpreter]: runs its awaited steps now, in the coroutine of the caller, see
   * [PythonInterpreterUpdateStep.awaited], and requests the other steps, such as the skeletons, in the background.
   * Returns when the facts are read, with the result of the awaited steps.
   *
   * The awaited steps run in the context of the caller, so a caller under a modal progress gets their write actions in
   * its modality. The queue would run them in no modality, which a modal caller that waits for them never lets run.
   */
  suspend fun update(interpreter: PythonInterpreter, request: PythonInterpreterUpdateRequest): PyResult<Unit> {
    val outcome = runSteps(interpreter, request, PythonInterpreterUpdateStep.EP_NAME.extensionList.filter { it.awaited })
    if (outcome.goOn) request(interpreter.sdk, request.copy(awaitedStepsDone = true))
    return outcome.result
  }

  /** Waits until no update of [interpreter] runs or waits. */
  suspend fun awaitUpdate(interpreter: PythonInterpreter) {
    val key = interpreter.key
    updates.first { key !in it }
  }

  /** Waits until no update runs or waits. */
  suspend fun awaitIdle() {
    updates.first { it.isEmpty() }
  }

  private suspend fun runUpdates(key: PythonInterpreterKey) {
    var done = false
    try {
      while (true) {
        val waiting = take(key) ?: break
        permits.withPermit { update(waiting) }
      }
      done = true
    }
    finally {
      // A failed or cancelled update drops the request that waits, so the interpreter does not stay busy.
      if (!done) updates.update { it - key }
    }
  }

  /** Takes the request that waits for [key], or ends the updates of [key] when none waits. */
  private fun take(key: PythonInterpreterKey): Waiting? {
    var taken: Waiting? = null
    updates.update { map ->
      taken = map[key]
      if (taken == null) map - key else map + (key to null)
    }
    return taken
  }

  private suspend fun update(waiting: Waiting) {
    val sdk = waiting.sdk
    val interpreter = sdk.pythonInterpreterResultAsync(forceRefresh = true).getOr {
      LOG.warn("No update of ${sdk.name}: ${it.error.message}")
      return
    }
    val steps = PythonInterpreterUpdateStep.EP_NAME.extensionList.let { all ->
      if (waiting.request.awaitedStepsDone) all.filterNot { it.awaited } else all
    }
    coroutineScope {
      val job = launch {
        withBackgroundProgress(project, PySdkBundle.message("python.sdk.updating.interpreter"), cancellable = false) {
          runSteps(interpreter, waiting.request, steps)
        }
        project.serviceIfCreated<DaemonCodeAnalyzer>()?.restart("Python interpreter updated")
      }
      // The disposal of the SDK stops the processes of its update, such as the skeleton generation. The end of the update
      // removes the child from the SDK, so the updates of a long session do not pile up on it.
      if (sdk is Disposable) {
        val cancelOnDisposal = Disposable { job.cancel() }
        if (Disposer.tryRegister(sdk, cancelOnDisposal)) job.invokeOnCompletion { Disposer.dispose(cancelOnDisposal) } else job.cancel()
      }
    }
  }

  /** What [runSteps] gives: whether the later steps may run, and the first failure of a step. */
  private class Outcome(val goOn: Boolean, val result: PyResult<Unit>)

  /** Runs [steps] in their order, until one stops the update. */
  private suspend fun runSteps(
    interpreter: PythonInterpreter,
    request: PythonInterpreterUpdateRequest,
    steps: List<PythonInterpreterUpdateStep>,
  ): Outcome {
    var failure: PyError? = null
    for (step in steps) {
      val goOn = step.update(project, interpreter, request) { error ->
        LOG.warn("The update of ${interpreter.sdk.name} failed: ${error.message}")
        if (failure == null) failure = error
      }
      if (!goOn) return Outcome(false, failure?.let { PyResult.failure(it) } ?: PyResult.success(Unit))
    }
    return Outcome(true, failure?.let { PyResult.failure(it) } ?: PyResult.success(Unit))
  }


  companion object {
    private val LOG = logger<PythonInterpreterUpdater>()
    private const val MAX_UPDATES = 2

    @Volatile
    private var enabledInTests = false

    @JvmStatic
    fun getInstance(project: Project): PythonInterpreterUpdater = project.service()

    /** Lets a unit test run the updates. A test then waits for an update, see [awaitUpdate]. */
    @TestOnly
    @JvmStatic
    fun setEnabledInTests(enabled: Boolean) {
      enabledInTests = enabled
    }

    /** The key of the interpreter of [sdk], read from its data without the file I/O of building the interpreter. */
    private fun updateKeyOf(sdk: Sdk): PythonInterpreterKey? {
      val ref = interpreterRefOf(sdk) ?: return null
      val ownerDir = (sdk.sdkAdditionalData as? PythonSdkAdditionalData)?.let { projectDirOf(it) } ?: return null
      return PythonInterpreterKey(ownerDir, ref)
    }
  }
}
