// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Prepares the environment strictly before a Python process is created, on every launch path of a
 * Python run configuration: plain Run, Debug (pydevd or debugpy/DAP), and test runners.
 * `environment.runProfile` is always an [AbstractPythonRunConfiguration].
 *
 * Unlike a [com.intellij.execution.BeforeRunTaskProvider] task, a preparer is not attached to (and
 * thus never serialized into) run configurations; it applies to all of them unconditionally
 * (PY-90571), so implementations must decide quickly whether there is any work to do.
 *
 * A preparer may suspend until its work is done (e.g. run a build under a background progress): the
 * Python process is not created until every preparer returns. Preparers are auxiliary and cannot
 * break or cancel the launch — see [prepareAll].
 */
@ApiStatus.Internal
interface PyLaunchPreparer {
  suspend fun prepareLaunch(environment: ExecutionEnvironment)

  companion object {
    private val EP_NAME: ExtensionPointName<PyLaunchPreparer> = ExtensionPointName("Pythonid.pyLaunchPreparer")
    private val LOG = logger<PyLaunchPreparer>()

    /**
     * Runs the preparers sequentially, each as an [async] child of a [supervisorScope], so that a
     * preparer can neither break nor cancel the launch. The supervisor keeps a child's failure
     * inside its [kotlinx.coroutines.Deferred] until [kotlinx.coroutines.Deferred.await], so every
     * outcome is handled in one place:
     *  - a failure is logged and the launch proceeds (an exception leaking into the launch
     *    pipeline would kill it without even a processNotStarted event);
     *  - a preparer cancelling itself (e.g. the user cancels its build progress) is absorbed —
     *    [kotlinx.coroutines.ensureActive] confirms the launch itself is still active;
     *  - cancelling the launch itself rethrows and cancels the running preparer.
     */
    @Suppress("PyExceptionTooBroad") // deliberate isolation barrier
    suspend fun prepareAll(environment: ExecutionEnvironment) {
      supervisorScope {
        for (preparer in EP_NAME.extensionList) {
          val result = async { preparer.prepareLaunch(environment) }
          try {
            result.await()
          }
          catch (@Suppress("IncorrectCancellationExceptionHandling") _: CancellationException) {
            currentCoroutineContext().ensureActive() // the launch itself is cancelled -> rethrow
            LOG.info("Launch preparer $preparer was cancelled; starting the run anyway")
          }
          catch (e: Exception) {
            LOG.warn("Launch preparer $preparer failed; starting the run anyway", e)
          }
        }
      }
    }

    /** Blocking bridge for the non-suspend launch paths ([PythonCommandLineState.startProcess]). */
    @JvmStatic
    fun prepareAllBlocking(environment: ExecutionEnvironment) {
      runBlockingMaybeCancellable { prepareAll(environment) }
    }
  }
}
