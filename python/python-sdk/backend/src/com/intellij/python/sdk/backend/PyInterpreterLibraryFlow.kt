// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

private const val INTERPRETER_LIBRARY_FLOW_KEY = "python.interpreter.library.entries"

/**
 * Whether a module names its interpreter by its interpreter library entry, `Python: <owner>:<ref>`, and every reader
 * reads that entry. The `python.interpreter.library.entries` registry key turns it on. When it is off, a module names
 * its interpreter by its Python SDK, as an older build does, and the readers read that SDK.
 *
 * The key needs a restart, so one session keeps one flow.
 */
@ApiStatus.Internal
fun isInterpreterLibraryFlowEnabled(): Boolean = Registry.`is`(INTERPRETER_LIBRARY_FLOW_KEY)
