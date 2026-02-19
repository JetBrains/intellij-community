// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.pipenv

import com.intellij.ide.util.PropertiesComponent
import org.jetbrains.annotations.SystemDependent

private const val PIPENV_PATH_SETTING: String = "PyCharm.Pipenv.Path"

/**
 * Tells if the SDK was added as pipenv.
 * The user-set persisted a path to the pipenv executable.
 */
var PropertiesComponent.pipenvPath: @SystemDependent String?
  get() = getValue(PIPENV_PATH_SETTING)
  set(value) {
    setValue(PIPENV_PATH_SETTING, value)
  }
