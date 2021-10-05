// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.run.configuration

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.diagnostic.logging.LogsGroupFragment
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.ui.*
import com.intellij.openapi.externalSystem.service.execution.configuration.*
import com.intellij.openapi.externalSystem.service.ui.getSelectedJdkReference
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.externalSystem.service.ui.setSelectedJdkReference
import com.intellij.openapi.externalSystem.service.ui.util.LabeledSettingsFragmentInfo
import com.intellij.openapi.externalSystem.service.ui.util.PathFragmentInfo
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel.Companion.createProjectJdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.openapi.roots.ui.distribution.FileChooserInfo
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.ui.components.JBTextField
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.execution.run.configuration.MavenDistributionsInfo.Companion.asDistributionInfo
import org.jetbrains.idea.maven.execution.run.configuration.MavenDistributionsInfo.Companion.asMavenHome
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenUtil

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
      val workingDirectoryFragment = addWorkingDirectoryFragment()
      addCommandLineFragment(workingDirectoryFragment)
      addProfilesFragment(workingDirectoryFragment)

      addJreFragment()
      addEnvironmentFragment()
      addVmOptionsFragment()

      addDistributionFragment()
      addUserSettingsFragment()
      addLocalRepositoryFragment()
      addThreadsFragment()

      add(LogsGroupFragment())

      addTag(
        "maven.skip.tests.tag",
        MavenConfigurableBundle.message("maven.settings.runner.skip.tests"),
        MavenConfigurableBundle.message("maven.run.configuration.options.group"),
        null,
        { runnerSettings.isSkipTests },
        { runnerSettings.isSkipTests = it }
      )
      addTag(
        "maven.use.plugin.registry.tag",
        MavenConfigurableBundle.message("maven.settings.general.use.plugin.registry"),
        MavenConfigurableBundle.message("maven.run.configuration.options.group"),
        MavenConfigurableBundle.message("maven.settings.general.use.plugin.registry.tooltip"),
        { generalSettings.isUsePluginRegistry },
        { generalSettings.isUsePluginRegistry = it }
      )
      addTag(
        "maven.print.stacktraces.tag",
        MavenConfigurableBundle.message("maven.settings.general.print.stacktraces"),
        MavenConfigurableBundle.message("maven.run.configuration.options.group"),
        MavenConfigurableBundle.message("maven.settings.general.print.stacktraces.tooltip"),
        { generalSettings.isPrintErrorStackTraces },
        { generalSettings.isPrintErrorStackTraces = it }
      )
      addTag(
        "maven.update.snapshots.tag",
        MavenConfigurableBundle.message("maven.settings.general.update.snapshots"),
        MavenConfigurableBundle.message("maven.run.configuration.options.group"),
        MavenConfigurableBundle.message("maven.settings.general.update.snapshots.tooltip"),
        { generalSettings.isAlwaysUpdateSnapshots },
        { generalSettings.isAlwaysUpdateSnapshots = it }
      )
      addTag(
        "maven.workspace.artifacts.tag",
        MavenConfigurableBundle.message("maven.settings.runner.resolve.workspace.artifacts"),
        MavenConfigurableBundle.message("maven.run.configuration.options.group"),
        MavenConfigurableBundle.message("maven.settings.runner.resolve.workspace.artifacts.tooltip"),
        { runnerParameters.isResolveToWorkspace },
        { runnerParameters.isResolveToWorkspace = it }
      )
      addTag(
        "maven.execute.recursively.tag",
        MavenConfigurableBundle.message("maven.settings.general.execute.non.recursively"),
        MavenConfigurableBundle.message("maven.run.configuration.options.group"),
        MavenConfigurableBundle.message("maven.settings.general.execute.recursively.tooltip"),
        { generalSettings.isNonRecursive },
        { generalSettings.isNonRecursive = it }
      )
      addTag(
        "maven.work.offline.tag",
        MavenConfigurableBundle.message("maven.settings.general.work.offline"),
        MavenConfigurableBundle.message("maven.run.configuration.options.group"),
        MavenConfigurableBundle.message("maven.settings.general.work.offline.tooltip"),
        { generalSettings.isWorkOffline },
        { generalSettings.isWorkOffline = it }
      )
      addVariantTag(
        "maven.checksum.policy.tag",
        MavenConfigurableBundle.message("maven.run.configuration.checksum.policy"),
        MavenConfigurableBundle.message("maven.run.configuration.options.group"),
        { generalSettings.checksumPolicy },
        { generalSettings.checksumPolicy = it },
        { it.displayString }
      )
      addVariantTag(
        "maven.output.level.tag",
        MavenConfigurableBundle.message("maven.run.configuration.output.level"),
        MavenConfigurableBundle.message("maven.run.configuration.options.group"),
        { generalSettings.outputLevel },
        { generalSettings.outputLevel = it },
        { it.displayString }
      )
      addVariantTag(
        "maven.multiproject.build.policy.tag",
        MavenConfigurableBundle.message("maven.run.configuration.multiproject.build.policy"),
        MavenConfigurableBundle.message("maven.run.configuration.options.group"),
        { generalSettings.failureBehavior },
        { generalSettings.failureBehavior = it },
        { it.displayString }
      )
    }
  }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addDistributionFragment() =
    addDistributionFragment(
      project,
      MavenDistributionsInfo(),
      { asDistributionInfo(generalSettings.mavenHome.ifEmpty { MavenServerManager.BUNDLED_MAVEN_3 }) },
      { generalSettings.mavenHome = it?.let(::asMavenHome) ?: MavenServerManager.BUNDLED_MAVEN_3 }
    ).addValidation {
      if (!MavenUtil.isValidMavenHome(it.generalSettings.mavenHome)) {
        throw RuntimeConfigurationError(MavenConfigurableBundle.message("maven.run.configuration.distribution.invalid.home.error"))
      }
    }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addWorkingDirectoryFragment() =
    addWorkingDirectoryFragment(
      project,
      MavenWorkingDirectoryInfo(project),
      { runnerParameters.workingDirPath },
      { runnerParameters.workingDirPath = it }
    )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addCommandLineFragment(
    workingDirectoryFragment: SettingsEditorFragment<MavenRunConfiguration, LabeledComponent<WorkingDirectoryField>>
  ) = addCommandLineFragment(
    project,
    MavenCommandLineInfo(project, workingDirectoryFragment.component().component),
    { runnerParameters.commandLine },
    { runnerParameters.commandLine = it }
  )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addEnvironmentFragment() =
    addEnvironmentFragment(
      { runnerSettings.environmentProperties },
      { runnerSettings.environmentProperties = it },
      { runnerSettings.isPassParentEnv },
      { runnerSettings.isPassParentEnv = it }
    )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addVmOptionsFragment() =
    addVmOptionsFragment(
      { runnerSettings.vmOptions.ifEmpty { null } },
      { runnerSettings.setVmOptions(it) }
    )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addJreFragment() =
    SdkLookupProvider.getInstance(project, object : SdkLookupProvider.Id {})
      .let { sdkLookupProvider ->
        add(createLabeledSettingsEditorFragment(
          SdkComboBox(createProjectJdkComboBoxModel(project, this@MavenRunConfigurationSettingsEditor)),
          object : LabeledSettingsFragmentInfo {
            override val editorLabel: String = MavenConfigurableBundle.message("maven.run.configuration.jre.label")
            override val settingsId: String = "maven.jre.fragment"
            override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.jre.name")
            override val settingsGroup: String = ExecutionBundle.message("group.java.options")
            override val settingsHint: String? = null
            override val settingsActionHint: String = MavenConfigurableBundle.message("maven.run.configuration.jre.action.hint")
          },
          { getSelectedJdkReference(sdkLookupProvider) },
          { setSelectedJdkReference(sdkLookupProvider, it) },
          { MavenRunnerSettings.USE_PROJECT_JDK },
          { runnerSettings.jreName },
          { runnerSettings.setJreName(it) }
        ))
      }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addProfilesFragment(
    workingDirectoryFragment: SettingsEditorFragment<MavenRunConfiguration, LabeledComponent<WorkingDirectoryField>>
  ) = add(
    createLabeledSettingsEditorFragment(
      MavenProfilesFiled(project, workingDirectoryFragment.component().component),
      object : LabeledSettingsFragmentInfo {
        override val editorLabel: String = MavenConfigurableBundle.message("maven.run.configuration.profiles.label")

        override val settingsId: String = "maven.profiles.fragment"
        override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.profiles.name")
        override val settingsGroup: String = MavenConfigurableBundle.message("maven.run.configuration.options.group")
        override val settingsHint: String = MavenConfigurableBundle.message("maven.run.configuration.profiles.hint")
        override val settingsActionHint: String? = null
      },
      { profiles },
      { profiles = it },
      { runnerParameters.profilesMap.ifEmpty { null } },
      { runnerParameters.profilesMap = it ?: emptyMap() }
    )
  )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addUserSettingsFragment() =
    addPathFragment(
      project,
      object : PathFragmentInfo {
        override val editorLabel: String = MavenConfigurableBundle.message("maven.run.configuration.user.settings.label")

        override val settingsId: String = "maven.user.settings.fragment"
        override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.user.settings.name")
        override val settingsGroup: String = MavenConfigurableBundle.message("maven.run.configuration.options.group")

        override val fileChooserTitle: String = MavenConfigurableBundle.message("maven.run.configuration.user.settings.title")
        override val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        override val fileChooserMacroFilter = FileChooserInfo.DIRECTORY_PATH
      },
      { generalSettings.userSettingsFile },
      { generalSettings.setUserSettingsFile(it) }
    )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addLocalRepositoryFragment() =
    addPathFragment(
      project,
      object : PathFragmentInfo {
        override val editorLabel: String = MavenConfigurableBundle.message("maven.run.configuration.local.repository.label")

        override val settingsId: String = "maven.local.repository.fragment"
        override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.local.repository.name")
        override val settingsGroup: String = MavenConfigurableBundle.message("maven.run.configuration.options.group")

        override val fileChooserTitle: String = MavenConfigurableBundle.message("maven.run.configuration.local.repository.title")
        override val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        override val fileChooserMacroFilter = FileChooserInfo.DIRECTORY_PATH
      },
      { generalSettings.localRepository },
      { generalSettings.setLocalRepository(it) }
    )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addThreadsFragment() = add(
    createLabeledTextSettingsEditorFragment<MavenRunConfiguration, JBTextField>(
      JBTextField(),
      object : LabeledSettingsFragmentInfo {
        override val editorLabel: String = MavenConfigurableBundle.message("maven.run.configuration.threads.label")
        override val settingsId: String = "maven.threads.fragment"
        override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.threads.name")
        override val settingsGroup: String = MavenConfigurableBundle.message("maven.run.configuration.options.group")
        override val settingsHint: String = MavenConfigurableBundle.message("maven.settings.general.thread.count.tooltip")
        override val settingsActionHint: String? = null
      },
      { generalSettings.threads },
      { generalSettings.threads = it }
    )
  )
}