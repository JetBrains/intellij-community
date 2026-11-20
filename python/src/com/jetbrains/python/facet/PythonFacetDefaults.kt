// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PythonFacetDefaults")

package com.jetbrains.python.facet

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.isInterpreterLibraryFlowEnabled
import com.jetbrains.python.sdk.PythonSdkType
import org.jetbrains.annotations.ApiStatus

/**
 * The SDK of a new Python facet. In the interpreter library flow it is `null`: the interpreter library entry of the
 * module names its interpreter. A Python SDK of the SDK table there is a leftover of an older build, and
 * `migrateSdkReferences` would make it the interpreter of the module. In the legacy flow it is the first Python SDK of
 * the SDK table.
 */
@ApiStatus.Internal
fun defaultPythonFacetSdk(): Sdk? {
  if (isInterpreterLibraryFlowEnabled()) return null
  return ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance()).firstOrNull()
}
