// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.internal

import com.jetbrains.python.PyInternalExecApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@NonNls
@get:ApiStatus.Internal
const val PYTHON_MODULE_ID: @NonNls String = "PYTHON_MODULE"

@NonNls
@get:ApiStatus.Internal
@PyInternalExecApi
const val PYTHON_FACET_ID: @NonNls String = "Python"