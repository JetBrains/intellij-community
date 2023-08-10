// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.openapi.options.SettingsEditor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyRunConfigurationEditorFactory {
  fun createEditor(configuration: AbstractPythonRunConfiguration<*>): SettingsEditor<AbstractPythonRunConfiguration<*>>
}