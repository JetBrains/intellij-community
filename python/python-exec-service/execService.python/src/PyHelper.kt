// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.python

/**
 * [name] of the helper file in helpers module.
 * Some helpers also need a small dependnecy module resided in [com.jetbrains.python.impl.PY3_HELPER_DEPENDENCIES_DIR]
 * In this case, set [addDependency]
 */
data class PyHelper(internal val name: String, internal val addDependency: Boolean = false)