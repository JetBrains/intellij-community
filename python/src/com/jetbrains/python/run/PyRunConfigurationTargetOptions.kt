// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

class PyRunConfigurationTargetOptions : PyRunConfigurationEditorExtension {

  override fun accepts(configuration: AbstractPythonRunConfiguration<out AbstractPythonRunConfiguration<*>>): PyRunConfigurationEditorFactory? {
    return null
  }

}