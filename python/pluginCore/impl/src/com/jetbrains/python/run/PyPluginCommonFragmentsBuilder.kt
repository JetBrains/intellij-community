// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.execution.ui.SettingsEditorFragment
import com.jetbrains.python.run.configuration.PyInterpreterModeNotifier
import com.jetbrains.python.run.configuration.PyPathMappingsEditorFragment
import javax.swing.JPanel

class PyPluginCommonFragmentsBuilder: PyCommonFragmentsBuilder() {
  override fun <T : AbstractPythonRunConfiguration<*>> createEnvironmentFragments(fragments: MutableList<SettingsEditorFragment<T, *>>,
                                                                                  config: T) {
    val sdkFragment: SettingsEditorFragment<T, JPanel> = PyPluginSdkFragment()
    fragments.add(sdkFragment)

    fragments.add(createWorkingDirectoryFragment(config.project))
    fragments.add(createEnvParameters())
    fragments.add(PyPathMappingsEditorFragment(sdkFragment as PyInterpreterModeNotifier))
  }
}