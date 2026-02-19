// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * `python` or `python.exe`
 */
typealias PythonBinary = Path

/**
 * python home directory, virtual environment or a base one.
 */
typealias PythonHomePath = Path

/**
 * `
 * python --version
` *
 */
@ApiStatus.Internal
const val PYTHON_VERSION_ARG: String = "--version"