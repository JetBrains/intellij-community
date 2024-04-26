// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.impl.run

import com.jetbrains.python.run.EnvFileComponent.Companion.createEnvFilesFragment
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.PyCommonFragmentsBuilder
import com.jetbrains.python.run.configuration.PyInterpreterModeNotifier
import com.jetbrains.python.run.configuration.PyPathMappingsEditorFragment
import javax.swing.JPanel
import kotlin.io.path.Path

class PyPluginCommonFragmentsBuilder: PyCommonFragmentsBuilder() {
  override fun <T : AbstractPythonRunConfiguration<*>> createEnvironmentFragments(fragments: MutableList<SettingsEditorFragment<T, *>>,
                                                                                  config: T) {
    val sdkFragment: SettingsEditorFragment<T, JPanel> = PyPluginSdkFragment()
    fragments.add(sdkFragment)

    fragments.add(createWorkingDirectoryFragment(config.project))
    fragments.add(createEnvParameters())
    fragments.add(createEnvFilesFragment { VirtualFileManager.getInstance().findFileByNioPath(Path(config.getWorkingDirectorySafe())) })
    fragments.add(PyPathMappingsEditorFragment(sdkFragment as PyInterpreterModeNotifier))
  }
}