// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.target

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.target.CustomToolLanguageRuntimeType
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.PyBundle
import com.jetbrains.python.psi.icons.PythonPsiApiIcons
import org.jetbrains.annotations.Nls
import java.util.function.Supplier
import javax.swing.Icon

class PythonLanguageRuntimeType : LanguageRuntimeType<PythonLanguageRuntimeConfiguration>(TYPE_ID), CustomToolLanguageRuntimeType {
  override val displayName: @NlsSafe String = "Python"

  override val icon: Icon
    get() = PythonPsiApiIcons.Python

  override val configurableDescription: @Nls String
    get() = PyBundle.message("python.language.configure.label")

  override val launchDescription: @Nls String
    get() = PyBundle.message("python.language.run.label")

  override fun createIntrospector(config: PythonLanguageRuntimeConfiguration): Introspector<PythonLanguageRuntimeConfiguration>? =
    if (config.pythonInterpreterPath.isNotEmpty()) null else PythonLanguageIntrospectable(config)

  override fun createSerializer(config: PythonLanguageRuntimeConfiguration): PersistentStateComponent<*> = throw UnsupportedOperationException()

  override fun createDefaultConfig(): PythonLanguageRuntimeConfiguration = PythonLanguageRuntimeConfiguration()

  override fun duplicateConfig(config: PythonLanguageRuntimeConfiguration): PythonLanguageRuntimeConfiguration =
    duplicatePersistentComponent(this, config)

  /**
   * Return `false` because Python run configurations are not yet ready for "Run on:" functionality.
   */
  override fun isApplicableTo(runConfig: RunnerAndConfigurationSettings): Boolean = false

  override fun createConfigurable(project: Project,
                                  config: PythonLanguageRuntimeConfiguration,
                                  targetEnvironmentType: TargetEnvironmentType<*>,
                                  targetSupplier: Supplier<TargetEnvironmentConfiguration>): Configurable =
    PythonLanguageRuntimeUI(project, config, targetSupplier)

  override fun findLanguageRuntime(target: TargetEnvironmentConfiguration): PythonLanguageRuntimeConfiguration? =
    target.runtimes.findByType()

  companion object {
    const val TYPE_ID = "PythonLanguageRuntime"

    @JvmStatic
    fun getInstance() = EXTENSION_NAME.findExtensionOrFail(PythonLanguageRuntimeType::class.java)
  }
}