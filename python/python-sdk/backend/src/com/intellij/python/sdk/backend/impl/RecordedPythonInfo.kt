// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend.impl

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.sdk.backend.PySdkBundle
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkAdditionalData

// What an interpreter already records about itself, read by `PythonInterpreter.getPythonInfo`. Nothing runs here.

/** The leading `major.minor…` of a version string: an SDK records its own as `Python 3.12.1`. */
internal val VERSION_NUMBER_RE: Regex = Regex("""\d+\.\S+""")

/**
 * What [version] states, or `null` when there is no recorded version or it does not parse.
 *
 * [PythonInfo.freeThreaded] keeps its default. No recorded version carries it, so only running the interpreter can
 * report it.
 *
 * Both `getPythonInfo` functions read a recorded version, and this is the whole of what they share. What they do
 * without one is the difference between them. `PythonInterpreter.getPythonInfo` falls back to the SDK's own recorded
 * version and then reports an error, because it must never run anything. `PythonEnvironment.getPythonInfo` runs the
 * interpreter.
 *
 * Neither judges whether the level is supported. That is the caller's policy, and
 * `PythonInterpreterPresentationBuilder` applies it as its own marker, separate from a failure.
 */
internal fun recordedPythonInfo(version: @NlsSafe String?): PythonInfo? =
  version
    ?.let { LanguageLevel.fromPythonVersionSafe(it) }
    ?.let { PythonInfo(languageLevel = it, version = version) }

/**
 * Why this interpreter's configuration is broken whatever the interpreter itself would say: it is an environment a
 * project owns, and its SDK records no project. `null` when there is nothing wrong with it.
 *
 * A remote or non-Python SDK has no detected environment and is left alone.
 */
internal fun PythonInterpreter.associationProblem(): Result.Failure<PyError>? {
  val environment = pythonEnvironment ?: return null
  if (!environment.requiresAssociation || sdk.recordsProject) return null
  val where = environment.pythonHomePath?.toString() ?: sdk.name
  return Result.Failure(MessageError(PySdkBundle.message("python.sdk.validation.environment.without.project", where)))
}

/** Whether this SDK records the project its environment belongs to, in either of the two places that carry it. */
private val Sdk.recordsProject: Boolean
  get() {
    val data = sdkAdditionalData as? PythonSdkAdditionalData ?: return false
    return data.hasValidWorkingDirectory() || data.associatedModulePath != null
  }
