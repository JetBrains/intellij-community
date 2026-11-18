// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.impl.PythonInterpreterStore
import org.jetbrains.annotations.ApiStatus

/*
 * The boundary between a platform [Sdk] and a [PythonInterpreter]. The platform hands an [Sdk] to an order entry, a run
 * configuration or an SDK combo, and the caller converts it here at once. [PythonInterpreterRegistry] itself works only
 * with a [PyProject][com.jetbrains.python.project.PyProject], a [PythonInterpreter] and a [PythonInterpreterKey].
 */

/**
 * The interpreter of [sdk], whichever Python project it belongs to. Waits for the first computation of the registry.
 * Move the caller to the interpreter of its Python project.
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
suspend fun Project.findPythonInterpreter(sdk: Sdk): PythonInterpreter? =
  PythonInterpreterRegistry.getInstance(this).store.findBySdk(sdk)

/** [findPythonInterpreter] without the wait: `null` also before the first computation. */
@ApiStatus.Internal
@ApiStatus.Obsolete
fun Project.findPythonInterpreterIfReady(sdk: Sdk): PythonInterpreter? =
  PythonInterpreterRegistry.getInstance(this).store.findBySdkIfReady(sdk)

/**
 * The interpreter of [sdk] in the registry of any open project, or `null`. It does not wait. For a holder of run
 * parameters that has no project, such as the console settings, which still gets an SDK from older code.
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
fun findPythonInterpreterInOpenProjectsIfReady(sdk: Sdk): PythonInterpreter? =
  ProjectManager.getInstance().openProjects.firstNotNullOfOrNull { it.findPythonInterpreterIfReady(sdk) }

/**
 * Every SDK that this build uses, of every type and tier. In the interpreter library flow they are the SDKs of the open
 * projects. Otherwise they are the SDKs of the SDK table.
 *
 * Only for application-level code that has no project, such as the update of the SDK data when a plugin of credentials
 * types loads. Other code reads the interpreters of a Python project.
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
fun pythonSdksOfOpenProjects(): List<Sdk> = PythonInterpreterStore.usedSdks()

/**
 * Every SDK of this project, also a broken one, for the legacy interpreter settings that edit an [Sdk]. Other code reads
 * [PythonInterpreterRegistry.allInterpreters].
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
fun Project.pythonSdksForSettings(): List<Sdk> = PythonInterpreterRegistry.getInstance(this).store.projectSdks()
