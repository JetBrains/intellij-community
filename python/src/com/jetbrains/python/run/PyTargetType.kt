/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.run

//TODO: DOC
enum class PyTargetType(private val customName: String? = null) {
  PYTHON(PythonRunConfigurationForm.MODULE_NAME), PATH(PythonRunConfigurationForm.SCRIPT_PATH), CUSTOM;

  fun getCustomName() = customName ?: name.toLowerCase().capitalize()
}