// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.poetry

import com.intellij.ide.util.PropertiesComponent
import org.jetbrains.annotations.SystemDependent

private const val POETRY_PATH_SETTING: String = "PyCharm.Poetry.Path"

/**
 * Tells if the SDK was added as poetry.
 * The user-set persisted a path to the poetry executable.
 */
var PropertiesComponent.poetryPath: @SystemDependent String?
  get() = getValue(POETRY_PATH_SETTING)
  set(value) {
    setValue(POETRY_PATH_SETTING, value)
  }