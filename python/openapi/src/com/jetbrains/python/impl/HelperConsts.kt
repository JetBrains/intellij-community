// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl

/**
 * Small dependency in `helpers` dir that some helpers depend on
 */
const val PY3_HELPER_DEPENDENCIES_DIR: String = "py3only"

/**
 * See [PY3_HELPER_DEPENDENCIES_DIR] but for py2
 */
const val PY2_HELPER_DEPENDENCIES_DIR: String = "py2only"
