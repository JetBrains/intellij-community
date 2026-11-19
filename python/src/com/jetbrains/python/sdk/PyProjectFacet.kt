// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.module.Module
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.asPyProjectOrCandidate
import org.jetbrains.annotations.ApiStatus

/**
 * The [PyProject] of this module for a caller that sets up its interpreter. It adds no facet any more: a module of
 * another IDE is a candidate, and the interpreter that the caller sets makes it a Python project.
 */
@Deprecated("A module needs no Python facet to be a Python project.", ReplaceWith("asPyProjectOrCandidate()", "com.jetbrains.python.project.asPyProjectOrCandidate"))
@ApiStatus.Internal
suspend fun Module.asPyProjectAddingFacet(): PyProject? = asPyProjectOrCandidate()
