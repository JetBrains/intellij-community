// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import org.jetbrains.annotations.ApiStatus

/**
 * Python plugin enabled in a paid version only
 */
@ApiStatus.Internal
const val PYTHON_PROF_PLUGIN_ID: String = "Pythonid"

/**
 * Python plugin with core (free) tier functionality
 */
@ApiStatus.Internal
const val PYTHON_FREE_PLUGIN_ID: String = "PythonCore"
