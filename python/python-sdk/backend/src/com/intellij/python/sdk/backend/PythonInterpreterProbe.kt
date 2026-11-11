// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execute
import com.intellij.python.community.execService.python.PyHelper
import com.intellij.python.community.execService.python.ZeroCodeVersionStringTransformer
import com.intellij.python.community.execService.python.executeHelper
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PYTHON_VERSION_ARG
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.impl.asBinToExecuteImpl
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Reads the version and the `sys.path` of a Python environment by running its binary, see [PythonEnvironmentFacts].
 *
 * Only a local environment gets its `sys.path` here. A target environment gets its paths with its skeletons, because
 * they are local copies of the remote ones.
 */
@ApiStatus.Internal
object PythonInterpreterProbe {
  private val LOG = logger<PythonInterpreterProbe>()

  /**
   * Reads the facts of the environment of [sdk] and writes them straight into it, see
   * [PythonInterpreterDetails.writeLegacySdk]. For a legacy SDK that has no project.
   */
  @JvmStatic
  @RequiresBackgroundThread
  fun probeLegacySdkBlocking(sdk: Sdk): PyResult<Unit> = runBlockingMaybeCancellable {
    val binary = sdk.asBinToExecuteImpl().getOr { return@runBlockingMaybeCancellable PyResult.failure(it.error) }
    val facts = read(binary, sdk).getOr { return@runBlockingMaybeCancellable it }
    PythonInterpreterDetails.writeLegacySdk(sdk, facts.version, facts.sysPath.orEmpty())
    PyResult.success(Unit)
  }

  /** The facts of the environment that [binary] runs. [sdk] tells a target. */
  internal suspend fun read(binary: BinaryToExec, sdk: Sdk): PyResult<PythonEnvironmentFacts> {
    val version = ExecService().execute(binary, Args(PYTHON_VERSION_ARG), processOutputTransformer = ZeroCodeVersionStringTransformer)
      .getOr { return it }
    if (sdk.sdkAdditionalData is PyTargetAwareAdditionalData) return PyResult.success(PythonEnvironmentFacts(version, null))

    val start = System.currentTimeMillis()
    val output = ExecService().executeHelper(binary, PyHelper(SYSPATH_HELPER)).getOr { return it }
    LOG.info("Reading sys.path of ${sdk.homePath} took ${System.currentTimeMillis() - start} ms")
    val eelDescriptor = Path.of(requireNotNull(sdk.homePath) { "No home path for $sdk" }).getEelDescriptor()
    val sysPath = localSysPathOf(output.lines().filter { it.isNotBlank() }, eelDescriptor)
    return PyResult.success(PythonEnvironmentFacts(version, sysPath))
  }

  private const val SYSPATH_HELPER = "syspath.py"
}

/**
 * The awaited step of an environment update that reads its version and `sys.path`, see [PythonInterpreterProbe]. A
 * target keeps the `sys.path` that its skeletons gave: the probe reads none for it.
 */
internal class PythonEnvironmentProbeStep : PythonEnvironmentStep {
  override val awaited: Boolean get() = true

  override suspend fun update(context: PythonEnvironmentContext, facts: PythonEnvironmentFacts): PythonEnvironmentFacts =
    when (val read = PythonInterpreterProbe.read(context.binary, context.sdk)) {
      is Result.Success -> facts.copy(version = read.result.version ?: facts.version, sysPath = read.result.sysPath ?: facts.sysPath)
      is Result.Failure -> facts.copy(awaitedError = read.error)
    }
}

/**
 * The step of an interpreter update that updates its environment, see [PythonEnvironments.update], and writes the facts
 * of the environment as the facts of the interpreter in the project. A caller of [PythonInterpreterUpdater.update]
 * waits for it, and gets the failure of the probe.
 */
internal class PythonInterpreterFactsStep : PythonInterpreterUpdateStep {
  override val awaited: Boolean get() = true

  override suspend fun update(
    project: Project,
    interpreter: PythonInterpreter,
    request: PythonInterpreterUpdateRequest,
    failure: (PyError) -> Unit,
  ): Boolean {
    val facts = PythonEnvironments.getInstance().update(interpreter.sdk).getOr {
      failure(it.error)
      return true
    }
    facts.awaitedError?.let(failure)
    writeEnvironmentFacts(project, interpreter, facts)
    return true
  }
}

/**
 * The background step of an interpreter update that runs the background steps of its environment, such as the
 * skeleton generation, see [PythonEnvironments.updateInBackground], and writes the facts again: a target gets its
 * `sys.path` with its skeletons. A later step tells the user about [PythonEnvironmentFacts.backgroundError].
 */
internal class PythonInterpreterBackgroundFactsStep : PythonInterpreterUpdateStep {
  override suspend fun update(
    project: Project,
    interpreter: PythonInterpreter,
    request: PythonInterpreterUpdateRequest,
    failure: (PyError) -> Unit,
  ): Boolean {
    val facts = PythonEnvironments.getInstance().updateInBackground(interpreter.sdk).getOr {
      failure(it.error)
      return true
    }
    writeEnvironmentFacts(project, interpreter, facts)
    return true
  }
}

private suspend fun writeEnvironmentFacts(project: Project, interpreter: PythonInterpreter, facts: PythonEnvironmentFacts) {
  val details = PythonInterpreterDetails.getInstance(project)
  details.writeVersion(interpreter, facts.version)
  facts.sysPath?.let { details.writeSysPath(interpreter, it) }
}
