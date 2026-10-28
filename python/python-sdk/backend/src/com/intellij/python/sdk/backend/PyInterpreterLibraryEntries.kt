// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyInterpreterLibraryKt")

package com.intellij.python.sdk.backend

import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderEntry
import com.jetbrains.python.PyNames
import org.jetbrains.annotations.ApiStatus

/**
 * The id of the library kind of an interpreter library. The library type that draws it in the UI registers this kind,
 * and `PyInterpreterLibrarySync` sets it on each library it builds.
 */
@ApiStatus.Internal
const val PY_INTERPRETER_LIBRARY_KIND_ID: String = "python.interpreter"

/**
 * Whether this order entry is the interpreter library of its module, see [PyInterpreterLibraryName]: a project library
 * whose name parses. Code that skips the interpreter of a module skips this entry as it skips the SDK.
 */
@ApiStatus.Internal
fun OrderEntry.isPyInterpreterLibrary(): Boolean =
  this is LibraryOrderEntry && !isModuleLevel && PyInterpreterLibraryName.parse(libraryName ?: return false) != null

/** Whether this order entry is a Python SDK. */
private fun OrderEntry.isPythonSdkEntry(): Boolean = this is JdkOrderEntry && jdk?.sdkType?.name == PyNames.PYTHON_SDK_ID_NAME

/**
 * Whether code that walks the order entries skips this entry. A module names its interpreter by one carrier only, and
 * the platform loads the other one as well, see [isInterpreterLibraryFlowEnabled]:
 * * the interpreter library flow skips every Python SDK, also on a module that has no interpreter library yet. The
 *   migration removes it, see `migrateSdkReferences`, but the module can still inherit it;
 * * the legacy flow skips the interpreter library, which a session of the other flow can have written.
 */
@ApiStatus.Internal
fun OrderEntry.isIgnoredInterpreterEntry(): Boolean =
  if (isInterpreterLibraryFlowEnabled()) isPythonSdkEntry() else isPyInterpreterLibrary()

/**
 * Whether this order entry carries the interpreter of its module: the interpreter library in the interpreter library
 * flow, see [isInterpreterLibraryFlowEnabled]. A Python SDK in the legacy flow is a [JdkOrderEntry], which every reader
 * already treats as the interpreter.
 */
@ApiStatus.Internal
fun OrderEntry.isInterpreterLibraryCarrier(): Boolean = isInterpreterLibraryFlowEnabled() && isPyInterpreterLibrary()
