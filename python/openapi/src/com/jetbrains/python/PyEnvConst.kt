// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import org.jetbrains.annotations.ApiStatus

/**
 * Env varname of paths to be added to `sys.path` separated by path-separator
 */
@ApiStatus.Internal const val PYTHONPATH: String = "PYTHONPATH"

/**
 * Env varname of the encoding of `stdin`, `stdout` and `stderr`
 */
@ApiStatus.Internal const val PYTHONIOENCODING: String = "PYTHONIOENCODING"
