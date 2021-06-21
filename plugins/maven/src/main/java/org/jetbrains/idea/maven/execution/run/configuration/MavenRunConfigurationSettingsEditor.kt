// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.run.configuration

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.diagnostic.logging.LogsGroupFragment
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ui.*
import com.intellij.openapi.externalSystem.service.execution.configuration.*
import com.intellij.openapi.externalSystem.service.ui.command.line.CommandLineInfo
import com.intellij.openapi.externalSystem.service.ui.distribution.DistributionInfo
import com.intellij.openapi.externalSystem.service.ui.distribution.DistributionsInfo
import com.intellij.openapi.externalSystem.service.ui.distribution.LocalDistributionInfo
import com.intellij.openapi.externalSystem.service.ui.getSelectedJdkReference
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryInfo
import com.intellij.openapi.externalSystem.service.ui.setSelectedJdkReference
import com.intellij.openapi.project.Project
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
      addDistributionFragment(project, MavenDistributionsInfo())
      val workingDirectoryFragment =
        addWorkingDirectoryFragment(project, MavenWorkingDirectoryInfo(project))
      val workingDirectoryField = workingDirectoryFragment.component().component
      addCommandLineFragment(project, MavenCommandLineInfo(project, workingDirectoryField))
      addJreFragment()
      addEnvironmentFragment()
      addVmOptionsFragment()
      addMavenProfilesFragment(project, workingDirectoryField)
      add(LogsGroupFragment())
    }
  }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addDistributionFragment(
    project: Project,
    distributionsInfo: DistributionsInfo
  ) = addDistributionFragment(
    project,
    distributionsInfo,
    { asDistributionInfo(settings.mavenHome) },
    { settings.mavenHome = asMavenHome(it) },
    { validateMavenHome(it) }
  )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addWorkingDirectoryFragment(
    project: Project,
    workingDirectoryInfo: WorkingDirectoryInfo
  ) = addWorkingDirectoryFragment(
    project,
    workingDirectoryInfo,
    { settings.workingDirectory },
    { settings.workingDirectory = it }
  )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addCommandLineFragment(
    project: Project,
    commandLineInfo: CommandLineInfo
  ) = addCommandLineFragment(
    project,
    commandLineInfo,
    { settings.commandLine },
    { settings.commandLine = it }
  )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addEnvironmentFragment() =
    addEnvironmentFragment(
      { settings.environment },
      { settings.environment = it },
      { settings.isPassParentEnvs },
      { settings.isPassParentEnvs = it }
    )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addVmOptionsFragment() =
    addVmOptionsFragment(
      { settings.vmOptions },
      { settings.vmOptions = it }
    )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addJreFragment() = add(createJreFragment())

  private fun createJreFragment(): SettingsEditorFragment<MavenRunConfiguration, LabeledComponent<SdkComboBox>> {
    val sdkLookupProvider = SdkLookupProvider.getInstance(project, object : SdkLookupProvider.Id {})
    val jreComboBox = SdkComboBox(createProjectJdkComboBoxModel(project, this)).apply {
      CommonParameterFragments.setMonospaced(this)
    }
    val jreComboBoxLabel = MavenConfigurableBundle.message("maven.run.configuration.jre.label")
    return SettingsEditorFragment<MavenRunConfiguration, LabeledComponent<SdkComboBox>>(
      "maven.jre.fragment",
      MavenConfigurableBundle.message("maven.run.configuration.jre.name"),
      ExecutionBundle.message("group.java.options"),
      LabeledComponent.create(jreComboBox, jreComboBoxLabel, BorderLayout.WEST),
      { it, c -> c.component.setSelectedJdkReference(sdkLookupProvider, it.settings.jreName ?: MavenRunnerSettings.USE_PROJECT_JDK) },
      { it, c -> it.settings.jreName = if (c.isVisible) c.component.getSelectedJdkReference(sdkLookupProvider) else null },
      { !it.settings.jreName.isNullOrBlank() }
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

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addMavenProfilesFragment(
    project: Project,
    workingDirectoryField: WorkingDirectoryField
  ) = add(createMavenProfilesFragment(project, workingDirectoryField))

  private fun createMavenProfilesFragment(
    project: Project,
    workingDirectoryField: WorkingDirectoryField
  ): SettingsEditorFragment<MavenRunConfiguration, LabeledComponent<MavenProfilesFiled>> {
    val mavenProfilesFiled = MavenProfilesFiled(project, workingDirectoryField).apply {
      CommonParameterFragments.setMonospaced(this)
      FragmentedSettingsUtil.setupPlaceholderVisibility(this)
    }
    val mavenProfilesLabel = MavenConfigurableBundle.message("maven.run.configuration.profiles.label")
    return SettingsEditorFragment<MavenRunConfiguration, LabeledComponent<MavenProfilesFiled>>(
      "external.system.command.line.fragment",
      MavenConfigurableBundle.message("maven.run.configuration.profiles.name"),
      MavenConfigurableBundle.message("maven.run.configuration.options.group"),
      LabeledComponent.create(mavenProfilesFiled, mavenProfilesLabel, BorderLayout.WEST),
      SettingsEditorFragmentType.EDITOR,
      { it, c -> c.component.profiles = it.settings.profiles },
      { it, c -> it.settings.profiles = c.component.profiles },
      { it.settings.profiles.isNotEmpty() }
    ).apply {
      isCanBeHidden = true
      isRemovable = true
      setHint(MavenConfigurableBundle.message("maven.run.configuration.profiles.hint"))
    }
  }
}