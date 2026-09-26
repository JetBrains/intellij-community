package com.intellij.python.sdk.backend

import com.intellij.openapi.components.service
import com.intellij.python.community.execService.asBinToExec
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.python.sdk.backend.impl.recordedPythonInfo
import com.intellij.python.sdk.backend.service.ActivatableEnvironmentService
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.errorProcessing.PyResult

/** The activation environment for [this] Python environment. */
suspend fun PythonEnvironment.activationEnvironment(): PyResult<Map<String, String>> =
  service<ActivatableEnvironmentService>().activationEnvironment(this)

/**
 * The id of the provider that built this environment, or null when no provider claims it.
 *
 * Null is a real answer. An environment of a kind whose provider is not loaded has no name, and the caller must not
 * guess one. At most one provider declares any class, so the order of the list does not matter.
 */
val PythonEnvironment.kindId: String?
  get() = PythonEnvironmentProvider.EP_NAME.filterableLazySequence()
    .firstOrNull { it.instance?.environmentClass?.isInstance(this) == true }
    ?.id

/**
 * What this environment is: the version it records about itself, and the interpreter's own answer when it records none.
 *
 * The recorded answer costs no process. A virtualenv states its version in `pyvenv.cfg` and a conda environment in its
 * `conda-meta` entry, so most environments answer from a file read. A system interpreter and a Python 2.7-era
 * `virtualenv` write nothing down, and an unparseable recorded version is the same case, so those run the interpreter.
 * Running it also validates the environment and reports free threading, which no recorded version carries.
 *
 * This asks nothing of an SDK, so it says nothing about a project. Where an SDK is in hand and its association
 * matters, use [PythonInterpreter.getPythonInfo] instead, which reads the SDK's recorded version and never runs
 * anything.
 */
suspend fun PythonEnvironment.getPythonInfo(): PyResult<PythonInfo> =
  recordedPythonInfo(version)?.let { PyResult.success(it) }
  ?: pythonBinaryPath.asBinToExec().validatePythonAndGetInfo()
