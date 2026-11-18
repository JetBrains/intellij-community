// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.legacy

import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.PythonInterpreterRecords
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.interpreterOwnerProjectDir
import com.intellij.python.sdk.backend.isInterpreterLibraryFlowEnabled
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/*
 * The readers of the SDK names that older builds store: a module SDK, a run configuration, a facet. Only the snapshot
 * build and the migration of `python-pyproject` call them. They go away with the legacy flow, see
 * [isInterpreterLibraryFlowEnabled].
 */

/**
 * The interpreter whose SDK has the name [sdkName] that a module stores, or `null`. Waits for the first computation.
 * Only the legacy flow names an interpreter by its SDK name.
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
suspend fun PythonInterpreterRegistry.findBySdkName(sdkName: String): PythonInterpreter? = store.findBySdkName(sdkName)

/**
 * The key of the interpreter for the SDK named [sdkName], for the PyProject at [ownerDir], or `null` when there is no
 * such Python SDK, or when the project has no directory to name the owner relative to, see [interpreterOwnerProjectDir].
 * Only the migration of a module of an older build calls it, in the interpreter library flow.
 *
 * The import saves the record of the SDK, see [PythonInterpreterRecords], and the registry builds the interpreter from
 * its ref and that record. The key keeps the owner that the SDK names. The SDK of the table stays as it is, so an older
 * build that opens the project still finds it. The import does not wait for the interpreter: it cannot be built while
 * its environment is gone, and the record then stays until the environment comes back.
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
fun PythonInterpreterRegistry.importTableSdk(sdkName: String, ownerDir: Path): PythonInterpreterKey? = store.importTableSdk(sdkName, ownerDir)

/**
 * The URLs of the paths that the legacy flow transferred to a module from the SDK of the SDK table named [sdkName]: the
 * source roots that an `.iml` stores for it. Empty when the table has no such SDK. Only the migration of a module of an
 * older build calls it, which removes these roots: the interpreter library flow derives them, see
 * [PythonInterpreterDetails.sourceRoots].
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
fun PythonInterpreterRegistry.transferredPathsOfTableSdk(sdkName: String): Set<String> = store.transferredPathsOfTableSdk(sdkName)

/**
 * The interpreter for the SDK named [sdkName] that a run configuration of an older build stores, without the wait:
 * `null` also before the first computation. In the interpreter library flow the SDK of the table gives the key, and
 * [ownerDir] is the owner when the SDK names none. In the legacy flow the SDK name answers.
 */
@ApiStatus.Internal
@ApiStatus.Obsolete
fun PythonInterpreterRegistry.findBySdkNameIfReady(sdkName: String, ownerDir: Path?): PythonInterpreter? =
  store.findBySdkNameIfReady(sdkName, ownerDir)
