// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus

/**
 * Marker for SDKs that represent a Python binary that still has to be downloaded / installed
 * before it can be used. Held by [InstallableSelectableInterpreter] so the sealed interpreter
 * hierarchy can live in python-sdk without dragging in the installer module.
 */
@ApiStatus.Internal
interface InstallablePythonSdk : Sdk
