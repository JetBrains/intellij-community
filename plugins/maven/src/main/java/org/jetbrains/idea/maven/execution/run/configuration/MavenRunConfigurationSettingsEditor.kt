// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.run.configuration

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.diagnostic.logging.LogsGroupFragment
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.ui.*
import com.intellij.openapi.externalSystem.service.execution.configuration.*
import com.intellij.openapi.externalSystem.service.ui.distribution.DistributionInfo
import com.intellij.openapi.externalSystem.service.ui.distribution.LocalDistributionInfo
import com.intellij.openapi.externalSystem.service.ui.getSelectedJdkReference
import com.intellij.openapi.externalSystem.service.ui.setSelectedJdkReference
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel.Companion.createProjectJdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.layout.*
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.execution.run.configuration.MavenDistributionsInfo.Companion.asDistributionInfo
import org.jetbrains.idea.maven.execution.run.configuration.MavenDistributionsInfo.Companion.asMavenHome
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.utils.MavenUtil
import java.awt.BorderLayout
import java.io.File

class MavenRunConfigurationSettingsEditor(
  runConfiguration: MavenRunConfiguration
) : RunConfigurationFragmentedEditor<MavenRunConfiguration>(
  runConfiguration,
  runConfiguration.extensionsManager
) {
  override fun createRunFragments(): List<SettingsEditorFragment<MavenRunConfiguration, *>> {
    return SettingsFragmentsContainer.fragments {
      add(CommonParameterFragments.createRunHeader())
      addBeforeRunFragment(CompileStepBeforeRun.ID)
      addAll(BeforeRunFragment.createGroup())
      add(CommonTags.parallelRun())
      addDistributionFragment(
        project,
        MavenDistributionsInfo(),
        { asDistributionInfo(settings.mavenHome) },
        { settings.mavenHome = asMavenHome(it) },
        { validateMavenHome(it) }
      )
      val workingDirectoryFragment = addWorkingDirectoryFragment(
        project,
        MavenWorkingDirectoryInfo(project),
        { settings.workingDirectory },
        { settings.workingDirectory = it }
      )
      addCommandLineFragment(
        project,
        MavenCommandLineInfo(
          project,
          workingDirectoryFragment.component().component
        ),
        { settings.commandLine },
        { settings.commandLine = it }
      )
      addJdkFragment(
        { MavenRunnerSettings.USE_PROJECT_JDK },
        { settings.jreName },
        { settings.jreName = it }
      )
      addEnvironmentFragment(
        { settings.environment },
        { settings.environment = it },
        { settings.isPassParentEnvs },
        { settings.isPassParentEnvs = it }
      )
      addVmOptionsFragment(
        { settings.vmOptions },
        { settings.vmOptions = it }
      )
      add(LogsGroupFragment())
    }
  }

  private fun <C : RunConfigurationBase<*>> SettingsFragmentsContainer<C>.addJdkFragment(
    getDefaultJdk: C.() -> String?,
    getJdk: C.() -> String?,
    setJdk: C.(String?) -> Unit
  ) = add(createJdkFragment(getDefaultJdk, getJdk, setJdk))

  private fun <C : RunConfigurationBase<*>> createJdkFragment(
    getDefaultJdk: C.() -> String?,
    getJdk: C.() -> String?,
    setJdk: C.(String?) -> Unit
  ): SettingsEditorFragment<C, LabeledComponent<SdkComboBox>> {
    val sdkLookupProvider = SdkLookupProvider.getInstance(project, object : SdkLookupProvider.Id {})
    val jreComboBox = SdkComboBox(createProjectJdkComboBoxModel(project, this)).apply {
      CommonParameterFragments.setMonospaced(this)
    }
    val jreComboBoxLabel = MavenConfigurableBundle.message("maven.run.configuration.jre.label")
    return SettingsEditorFragment<C, LabeledComponent<SdkComboBox>>(
      "maven.jre.fragment",
      MavenConfigurableBundle.message("maven.run.configuration.jre.name"),
      ExecutionBundle.message("group.java.options"),
      LabeledComponent.create(jreComboBox, jreComboBoxLabel, BorderLayout.WEST),
      { it, c -> c.component.setSelectedJdkReference(sdkLookupProvider, it.getJdk() ?: it.getDefaultJdk()) },
      { it, c -> it.setJdk(if (c.isVisible) c.component.getSelectedJdkReference(sdkLookupProvider) else null) },
      { !it.getJdk().isNullOrBlank() }
    ).apply {
      isCanBeHidden = true
      isRemovable = true
      actionHint = MavenConfigurableBundle.message("maven.run.configuration.jre.action.hint")
    }
  }

  private fun ValidationInfoBuilder.validateMavenHome(distribution: DistributionInfo): ValidationInfo? {
    return when {
      distribution is MavenDistributionsInfo.Bundled2DistributionInfo -> null
      distribution is MavenDistributionsInfo.Bundled3DistributionInfo -> null
      distribution is MavenDistributionsInfo.WrappedDistributionInfo -> null
      distribution is LocalDistributionInfo && MavenUtil.isValidMavenHome(File(distribution.path)) -> null
      else -> error(MavenConfigurableBundle.message("maven.run.configuration.distribution.invalid.home.error"))
    }
  }
}