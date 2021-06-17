// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.run.configuration

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.diagnostic.logging.LogsGroupFragment
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.JavaRunConfigurationExtensionManager
import com.intellij.execution.ui.*
import com.intellij.openapi.externalSystem.service.execution.configuration.*
import com.intellij.openapi.externalSystem.service.ui.distribution.ExternalSystemDistributionInfo
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
  JavaRunConfigurationExtensionManager.instance
) {
  override fun createRunFragments(): List<SettingsEditorFragment<MavenRunConfiguration, *>> {
    return ArrayList<SettingsEditorFragment<MavenRunConfiguration, *>>().apply {
      val workingDirectory = createProjectPath(
        project,
        MavenWorkingDirectoryInfo(project),
        MavenRunConfiguration::getWorkingDirectory,
        MavenRunConfiguration::setWorkingDirectory
      )
      val tasksAndArguments = createTasksAndArguments(
        project,
        MavenTasksAndArgumentsInfo(project, workingDirectory.component().component),
        { commandLine ?: "" },
        MavenRunConfiguration::setCommandLine
      )

      add(CommonParameterFragments.createRunHeader())
      add(createBeforeRun(CompileStepBeforeRun.ID))
      addAll(BeforeRunFragment.createGroup())
      add(CommonTags.parallelRun())
      add(createDistribution(
        project,
        MavenDistributionsInfo(),
        { asDistributionInfo(mavenHome) },
        { mavenHome = asMavenHome(it) },
        { validateMavenHome(it) }))
      add(tasksAndArguments)
      add(workingDirectory)
      add(createJreCombobox())
      add(createEnvParameters(
        MavenRunConfiguration::getEnvironment, MavenRunConfiguration::setEnvironment,
        MavenRunConfiguration::isPassParentEnvs, MavenRunConfiguration::setPassParentEnvs))
      add(createVmOptions(
        MavenRunConfiguration::getVmOptions, MavenRunConfiguration::setVmOptions))
      add(LogsGroupFragment())
    }
  }

  private fun createJreCombobox(): SettingsEditorFragment<MavenRunConfiguration, LabeledComponent<SdkComboBox>> {
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
      { it, c -> c.component.setSelectedJdkReference(sdkLookupProvider, it.jreName) },
      { it, c -> it.jreName = if (c.isVisible) c.component.getSelectedJdkReference(sdkLookupProvider) else null },
      { !it.jreName.isNullOrBlank() }
    ).apply {
      isCanBeHidden = true
      isRemovable = true
      actionHint = MavenConfigurableBundle.message("maven.run.configuration.jre.action.hint")
    }
  }

  private fun ValidationInfoBuilder.validateMavenHome(distribution: ExternalSystemDistributionInfo): ValidationInfo? {
    return when {
      distribution is MavenDistributionsInfo.Bundled2DistributionInfo -> null
      distribution is MavenDistributionsInfo.Bundled3DistributionInfo -> null
      distribution is MavenDistributionsInfo.WrappedDistributionInfo -> null
      distribution is LocalDistributionInfo && MavenUtil.isValidMavenHome(File(distribution.path)) -> null
      else -> error(MavenConfigurableBundle.message("maven.run.configuration.distribution.invalid.home.error"))
    }
  }
}