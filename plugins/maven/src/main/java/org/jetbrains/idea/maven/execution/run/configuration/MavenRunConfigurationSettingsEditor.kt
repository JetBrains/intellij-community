// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.run.configuration

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.diagnostic.logging.LogsGroupFragment
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.ui.*
import com.intellij.ide.macro.MacrosDialog
import com.intellij.ide.wizard.getCanonicalPath
import com.intellij.ide.wizard.getPresentablePath
import com.intellij.openapi.externalSystem.service.execution.configuration.*
import com.intellij.openapi.externalSystem.service.ui.getSelectedJdkReference
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.externalSystem.service.ui.setSelectedJdkReference
import com.intellij.openapi.externalSystem.service.ui.util.LabeledSettingsFragmentInfo
import com.intellij.openapi.externalSystem.service.ui.util.PathFragmentInfo
import com.intellij.openapi.externalSystem.service.ui.util.SettingsFragmentInfo
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace
import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace.Companion.task
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicLazyProperty
import com.intellij.openapi.observable.properties.AtomicObservableProperty
import com.intellij.openapi.observable.properties.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel.Companion.createProjectJdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.openapi.roots.ui.distribution.DistributionComboBox
import com.intellij.openapi.roots.ui.distribution.FileChooserInfo
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.textFieldWithBrowseButton
import com.intellij.ui.layout.*
import com.intellij.util.ui.UIUtil
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.execution.run.configuration.MavenDistributionsInfo.Companion.asDistributionInfo
import org.jetbrains.idea.maven.execution.run.configuration.MavenDistributionsInfo.Companion.asMavenHome
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.project.MavenGeneralSettings
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.MavenWslUtil
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JPanel

class MavenRunConfigurationSettingsEditor(
  runConfiguration: MavenRunConfiguration
) : RunConfigurationFragmentedEditor<MavenRunConfiguration>(
  runConfiguration,
  runConfiguration.extensionsManager
) {
  private val resetOperation = AnonymousParallelOperationTrace()

  override fun resetEditorFrom(s: RunnerAndConfigurationSettingsImpl) {
    resetOperation.task {
      super.resetEditorFrom(s)
    }
  }

  override fun resetEditorFrom(settings: MavenRunConfiguration) {
    resetOperation.task {
      super.resetEditorFrom(settings)
    }
  }

  override fun createRunFragments() = SettingsFragmentsContainer.fragments<MavenRunConfiguration> {
    add(CommonParameterFragments.createRunHeader())
    addBeforeRunFragment(CompileStepBeforeRun.ID)
    addAll(BeforeRunFragment.createGroup())
    add(CommonTags.parallelRun())
    val workingDirectoryFragment = addWorkingDirectoryFragment()
    addCommandLineFragment(workingDirectoryFragment)
    addMavenOptionsGroupFragment()
    addJavaOptionsGroupFragment(workingDirectoryFragment)
    add(LogsGroupFragment())
  }

  private val MavenRunConfiguration.generalSettingsOrDefault: MavenGeneralSettings
    get() = generalSettings ?: MavenProjectsManager.getInstance(project).generalSettings.clone()

  private val MavenRunConfiguration.runnerSettingsOrDefault: MavenRunnerSettings
    get() = runnerSettings ?: MavenRunner.getInstance(project).settings.clone()

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addMavenOptionsGroupFragment() =
    add(object : NestedGroupFragment<MavenRunConfiguration>(
      "maven.runner.group",
      MavenConfigurableBundle.message("maven.run.configuration.general.options.group.name"),
      MavenConfigurableBundle.message("maven.run.configuration.general.options.group"),
      { true }
    ) {
      override fun createChildren() = SettingsFragmentsContainer.fragments<MavenRunConfiguration> {
        inheritCheckBoxGroup(
          "maven.runner.group.inherit",
          MavenConfigurableBundle.message("maven.run.configuration.general.options.group.inherit"),
          { it, c -> c.isSelected = it.generalSettings == null },
          { it, c -> it.generalSettings = if (c.isSelected) null else it.generalSettingsOrDefault }
        ) {
          val distributionComponent = addDistributionFragment().component().component
          val userSettingsComponent = addUserSettingsFragment().component().component
          addLocalRepositoryFragment(distributionComponent, userSettingsComponent)
          addOutputLevelFragment()
          addThreadsFragment()
          addUsePluginRegistryTag()
          addPrintStacktracesTag()
          addUpdateSnapshotsTag()
          addExecuteNonRecursivelyTag()
          addWorkOfflineTag()
          addCheckSumPolicyTag()
          addMultiprojectBuildPolicyTag()
        }
      }
    }).apply { isRemovable = false }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addJavaOptionsGroupFragment(
    workingDirectoryFragment: SettingsEditorFragment<MavenRunConfiguration, LabeledComponent<WorkingDirectoryField>>
  ) = add(object : NestedGroupFragment<MavenRunConfiguration>(
    "maven.runner.group",
    MavenConfigurableBundle.message("maven.run.configuration.runner.options.group.name"),
    MavenConfigurableBundle.message("maven.run.configuration.runner.options.group"),
    { true }
  ) {
    override fun createChildren() = SettingsFragmentsContainer.fragments<MavenRunConfiguration> {
      inheritCheckBoxGroup(
        "maven.runner.group.inherit",
        MavenConfigurableBundle.message("maven.run.configuration.runner.options.group.inherit"),
        { it, c -> c.isSelected = it.runnerSettings == null },
        { it, c -> it.runnerSettings = if (c.isSelected) null else it.runnerSettingsOrDefault }
      ) {
        addJreFragment()
        addEnvironmentFragment()
        addVmOptionsFragment()
        addProfilesFragment(workingDirectoryFragment)
        addSkipTestsTag()
        addResolveWorkspaceArtifactsTag()
      }
    }
  }).apply { isRemovable = false }

  private fun <S> SettingsFragmentsContainer<S>.inheritCheckBoxGroup(
    id: String,
    label: @NlsContexts.Checkbox String,
    reset: (S, JCheckBox) -> Unit,
    apply: (S, JCheckBox) -> Unit,
    configure: SettingsFragmentsContainer<S>.() -> Unit
  ) {
    val fragments = SettingsFragmentsContainer.fragments(configure)
    addInheritCheckBoxFragment(id, label, reset, apply)
      .applyToComponent { bind(fragments) }
    fragments.forEach(::add)
  }

  private fun <S> SettingsFragmentsContainer<S>.addInheritCheckBoxFragment(
    id: String,
    label: @NlsContexts.Checkbox String,
    reset: (S, JCheckBox) -> Unit,
    apply: (S, JCheckBox) -> Unit,
  ) = addSettingsEditorFragment(
    JCheckBox(label),
    object : SettingsFragmentInfo {
      override val settingsId: String = id
      override val settingsName: String? = null
      override val settingsGroup: String? = null
      override val settingsPriority: Int = 0
      override val settingsType = SettingsEditorFragmentType.EDITOR
      override val settingsHint: String? = null
      override val settingsActionHint: String? = null
    },
    reset,
    apply,
    { true }
  ).apply { isRemovable = false }

  private fun JCheckBox.bind(fragments: List<SettingsEditorFragment<*, *>>) {
    addItemListener {
      for (fragment in fragments) {
        val component = fragment.component()
        if (component != null) {
          UIUtil.setEnabledRecursively(component, !isSelected)
        }
      }
    }
    for (fragment in fragments) {
      fragment.addSettingsEditorListener {
        if (resetOperation.isOperationCompleted()) {
          isSelected = false
        }
      }
    }
  }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addSkipTestsTag() {
    addTag(
      "maven.skip.tests.tag",
      MavenConfigurableBundle.message("maven.settings.runner.skip.tests"),
      MavenConfigurableBundle.message("maven.run.configuration.runner.options.group"),
      null,
      { runnerSettingsOrDefault.isSkipTests },
      { runnerSettingsOrDefault.isSkipTests = it }
    )
  }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addUsePluginRegistryTag() {
    addTag(
      "maven.use.plugin.registry.tag",
      MavenConfigurableBundle.message("maven.settings.general.use.plugin.registry"),
      MavenConfigurableBundle.message("maven.run.configuration.general.options.group"),
      MavenConfigurableBundle.message("maven.settings.general.use.plugin.registry.tooltip"),
      { generalSettingsOrDefault.isUsePluginRegistry },
      { generalSettingsOrDefault.isUsePluginRegistry = it }
    )
  }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addPrintStacktracesTag() {
    addTag(
      "maven.print.stacktraces.tag",
      MavenConfigurableBundle.message("maven.settings.general.print.stacktraces"),
      MavenConfigurableBundle.message("maven.run.configuration.general.options.group"),
      MavenConfigurableBundle.message("maven.settings.general.print.stacktraces.tooltip"),
      { generalSettingsOrDefault.isPrintErrorStackTraces },
      { generalSettingsOrDefault.isPrintErrorStackTraces = it }
    )
  }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addUpdateSnapshotsTag() {
    addTag(
      "maven.update.snapshots.tag",
      MavenConfigurableBundle.message("maven.settings.general.update.snapshots"),
      MavenConfigurableBundle.message("maven.run.configuration.general.options.group"),
      MavenConfigurableBundle.message("maven.settings.general.update.snapshots.tooltip"),
      { generalSettingsOrDefault.isAlwaysUpdateSnapshots },
      { generalSettingsOrDefault.isAlwaysUpdateSnapshots = it }
    )
  }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addResolveWorkspaceArtifactsTag() {
    addTag(
      "maven.workspace.artifacts.tag",
      MavenConfigurableBundle.message("maven.settings.runner.resolve.workspace.artifacts"),
      MavenConfigurableBundle.message("maven.run.configuration.runner.options.group"),
      MavenConfigurableBundle.message("maven.settings.runner.resolve.workspace.artifacts.tooltip"),
      { runnerParameters.isResolveToWorkspace },
      { runnerParameters.isResolveToWorkspace = it }
    )
  }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addExecuteNonRecursivelyTag() {
    addTag(
      "maven.execute.non.recursively.tag",
      MavenConfigurableBundle.message("maven.settings.general.execute.non.recursively"),
      MavenConfigurableBundle.message("maven.run.configuration.general.options.group"),
      MavenConfigurableBundle.message("maven.settings.general.execute.recursively.tooltip"),
      { generalSettingsOrDefault.isNonRecursive },
      { generalSettingsOrDefault.isNonRecursive = it }
    )
  }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addWorkOfflineTag() {
    addTag(
      "maven.work.offline.tag",
      MavenConfigurableBundle.message("maven.settings.general.work.offline"),
      MavenConfigurableBundle.message("maven.run.configuration.general.options.group"),
      MavenConfigurableBundle.message("maven.settings.general.work.offline.tooltip"),
      { generalSettingsOrDefault.isWorkOffline },
      { generalSettingsOrDefault.isWorkOffline = it }
    )
  }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addCheckSumPolicyTag() {
    addVariantTag(
      "maven.checksum.policy.tag",
      MavenConfigurableBundle.message("maven.run.configuration.checksum.policy"),
      MavenConfigurableBundle.message("maven.run.configuration.general.options.group"),
      { generalSettingsOrDefault.checksumPolicy },
      { generalSettingsOrDefault.checksumPolicy = it },
      { it.displayString }
    )
  }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addOutputLevelFragment() =
    addVariantFragment(
      object : LabeledSettingsFragmentInfo {
        override val editorLabel: String = MavenConfigurableBundle.message("maven.run.configuration.output.level.label")
        override val settingsId: String = "maven.output.level.fragment"
        override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.output.level.name")
        override val settingsGroup: String = MavenConfigurableBundle.message("maven.run.configuration.general.options.group")
        override val settingsHint: String? = null
        override val settingsActionHint: String? = null
      },
      { generalSettingsOrDefault.outputLevel },
      { generalSettingsOrDefault.outputLevel = it },
      { it.displayString }
    )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addMultiprojectBuildPolicyTag() {
    addVariantTag(
      "maven.multiproject.build.policy.tag",
      MavenConfigurableBundle.message("maven.run.configuration.multiproject.build.policy"),
      MavenConfigurableBundle.message("maven.run.configuration.general.options.group"),
      { generalSettingsOrDefault.failureBehavior },
      { generalSettingsOrDefault.failureBehavior = it },
      { it.displayString }
    )
  }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addDistributionFragment() =
    addDistributionFragment(
      project,
      MavenDistributionsInfo(),
      { asDistributionInfo(generalSettingsOrDefault.mavenHome.ifEmpty { MavenServerManager.BUNDLED_MAVEN_3 }) },
      { generalSettingsOrDefault.mavenHome = it?.let(::asMavenHome) ?: MavenServerManager.BUNDLED_MAVEN_3 }
    ).addValidation {
      if (!MavenUtil.isValidMavenHome(it.generalSettingsOrDefault.mavenHome)) {
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
      object : LabeledSettingsFragmentInfo {
        override val editorLabel: String = ExecutionBundle.message("environment.variables.component.title")
        override val settingsId: String = "maven.environment.variables.fragment"
        override val settingsName: String = ExecutionBundle.message("environment.variables.fragment.name")
        override val settingsGroup: String = MavenConfigurableBundle.message("maven.run.configuration.runner.options.group")
        override val settingsHint: String = ExecutionBundle.message("environment.variables.fragment.hint")
        override val settingsActionHint: String = ExecutionBundle.message("set.custom.environment.variables.for.the.process")
      },
      { runnerSettingsOrDefault.environmentProperties },
      { runnerSettingsOrDefault.environmentProperties = it },
      { runnerSettingsOrDefault.isPassParentEnv },
      { runnerSettingsOrDefault.isPassParentEnv = it },
      hideWhenEmpty = true
    )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addVmOptionsFragment() =
    addVmOptionsFragment(
      object : LabeledSettingsFragmentInfo {
        override val editorLabel: String = ExecutionBundle.message("run.configuration.java.vm.parameters.label")
        override val settingsId: String = "maven.vm.options.fragment"
        override val settingsName: String = ExecutionBundle.message("run.configuration.java.vm.parameters.name")
        override val settingsGroup: String = MavenConfigurableBundle.message("maven.run.configuration.runner.options.group")
        override val settingsHint: String = ExecutionBundle.message("run.configuration.java.vm.parameters.hint")
        override val settingsActionHint: String = ExecutionBundle.message("specify.vm.options.for.running.the.application")
      },
      { runnerSettingsOrDefault.vmOptions.ifEmpty { null } },
      { runnerSettingsOrDefault.setVmOptions(it) }
    )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addJreFragment() =
    SdkLookupProvider.getInstance(project, object : SdkLookupProvider.Id {})
      .let { sdkLookupProvider ->
        addLabeledSettingsEditorFragment(
          SdkComboBox(createProjectJdkComboBoxModel(project, this@MavenRunConfigurationSettingsEditor)),
          object : LabeledSettingsFragmentInfo {
            override val editorLabel: String = MavenConfigurableBundle.message("maven.run.configuration.jre.label")
            override val settingsId: String = "maven.jre.fragment"
            override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.jre.name")
            override val settingsGroup: String = MavenConfigurableBundle.message("maven.run.configuration.runner.options.group")
            override val settingsHint: String? = null
            override val settingsActionHint: String = MavenConfigurableBundle.message("maven.run.configuration.jre.action.hint")
          },
          { getSelectedJdkReference(sdkLookupProvider) },
          { setSelectedJdkReference(sdkLookupProvider, it) },
          { MavenRunnerSettings.USE_PROJECT_JDK },
          { runnerSettingsOrDefault.jreName },
          { runnerSettingsOrDefault.setJreName(it) }
        )
      }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addProfilesFragment(
    workingDirectoryFragment: SettingsEditorFragment<MavenRunConfiguration, LabeledComponent<WorkingDirectoryField>>
  ) = addLabeledSettingsEditorFragment(
    MavenProfilesFiled(project, workingDirectoryFragment.component().component),
    object : LabeledSettingsFragmentInfo {
      override val editorLabel: String = MavenConfigurableBundle.message("maven.run.configuration.profiles.label")

      override val settingsId: String = "maven.profiles.fragment"
      override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.profiles.name")
      override val settingsGroup: String = MavenConfigurableBundle.message("maven.run.configuration.runner.options.group")
      override val settingsHint: String = MavenConfigurableBundle.message("maven.run.configuration.profiles.hint")
      override val settingsActionHint: String? = null
    },
    { profiles },
    { profiles = it },
    { runnerParameters.profilesMap.ifEmpty { null } },
    { runnerParameters.profilesMap = it ?: emptyMap() }
  )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addUserSettingsFragment() =
    addOverridablePathFragment(
      project,
      object : PathFragmentInfo {
        override val editorLabel: String = MavenConfigurableBundle.message("maven.run.configuration.user.settings.label")

        override val settingsId: String = "maven.user.settings.fragment"
        override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.user.settings.name")
        override val settingsGroup: String = MavenConfigurableBundle.message("maven.run.configuration.general.options.group")

        override val fileChooserTitle: String = MavenConfigurableBundle.message("maven.run.configuration.user.settings.title")
        override val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        override val fileChooserMacroFilter = FileChooserInfo.DIRECTORY_PATH
      },
      { generalSettingsOrDefault.userSettingsFile },
      { generalSettingsOrDefault.setUserSettingsFile(it) },
      {
        val mavenConfig = MavenProjectsManager.getInstance(project)?.generalSettings?.mavenConfig
        val userSettings = MavenWslUtil.getUserSettings(project, "", mavenConfig)
        getCanonicalPath(userSettings.path)
      }
    )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addLocalRepositoryFragment(
    distributionComponent: DistributionComboBox,
    userSettingsComponent: OverridablePathComponent
  ) = addOverridablePathFragment(
    project,
    object : PathFragmentInfo {
      override val editorLabel: String = MavenConfigurableBundle.message("maven.run.configuration.local.repository.label")

      override val settingsId: String = "maven.local.repository.fragment"
      override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.local.repository.name")
      override val settingsGroup: String = MavenConfigurableBundle.message("maven.run.configuration.general.options.group")

      override val fileChooserTitle: String = MavenConfigurableBundle.message("maven.run.configuration.local.repository.title")
      override val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
      override val fileChooserMacroFilter = FileChooserInfo.DIRECTORY_PATH
    },
    { generalSettingsOrDefault.localRepository },
    { generalSettingsOrDefault.setLocalRepository(it) },
    {
      val mavenConfig = MavenProjectsManager.getInstance(project)?.generalSettings?.mavenConfig
      val distributionInfo = distributionComponent.selectedDistribution
      val distribution = distributionInfo?.let(::asMavenHome) ?: MavenServerManager.BUNDLED_MAVEN_3
      val userSettingsFile = MavenWslUtil.getUserSettings(project, userSettingsComponent.path, mavenConfig)
      val userSettings = getCanonicalPath(userSettingsFile.path)
      val localRepository = MavenWslUtil.getLocalRepo(project, "", distribution, userSettings, mavenConfig)
      getCanonicalPath(localRepository.path)
    }
  ).applyToComponent {
    distributionComponent.addItemListener {
      component.updatePathState()
    }
    userSettingsComponent.addStateListener {
      component.updatePathState()
    }
  }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addOverridablePathFragment(
    project: Project,
    info: PathFragmentInfo,
    getPath: MavenRunConfiguration.() -> String,
    setPath: MavenRunConfiguration.(String) -> Unit,
    defaultPath: () -> String
  ) = addLabeledSettingsEditorFragment(
    OverridablePathComponent(project, info, defaultPath),
    info,
    { it, c -> c.path = it.getPath() },
    { it, c -> it.setPath(c.path) }
  )

  private class OverridablePathComponent(
    project: Project,
    info: PathFragmentInfo,
    private val defaultPath: () -> String
  ) : JPanel() {
    private val textField = textFieldWithBrowseButton(project, info)
    private val checkBox = JCheckBox(MavenConfigurableBundle.message("maven.run.configuration.override.checkbox"))

    private val overrideProperty = AtomicBooleanProperty(false)
    private val pathProperty = AtomicObservableProperty("")
    private val overriddenPathProperty = AtomicLazyProperty(defaultPath)

    private var isOverride by overrideProperty
    private var overriddenPath by overriddenPathProperty
    var path: String
      get() = if (isOverride) overriddenPath else ""
      set(path) {
        isOverride = path.isNotEmpty()
        updateOverriddenPathState(path)
        updatePathState()
      }

    init {
      layout = BorderLayout()
      add(textField, BorderLayout.CENTER)
      add(checkBox, BorderLayout.EAST)
      checkBox.bind(overrideProperty)
      textField.bind(pathProperty.transform(::getPresentablePath, ::getCanonicalPath))
      pathProperty.afterChange { updateOverriddenPathState(it) }
      overrideProperty.afterChange { updatePathState() }
      addStateListener { updateEnableState() }
      checkBox.addPropertyChangeListener("enabled") { updateEnableState() }
    }

    fun addStateListener(listener: () -> Unit) {
      pathProperty.afterChange { listener() }
      overrideProperty.afterChange { listener() }
    }

    fun updatePathState() {
      pathProperty.set(if (isOverride) overriddenPath else defaultPath())
    }

    private fun updateOverriddenPathState(path: String) {
      if (isOverride) {
        overriddenPath = path
      }
    }

    private fun updateEnableState() {
      textField.isEnabled = checkBox.isEnabled && isOverride
    }

    fun textFieldWithBrowseButton(
      project: Project,
      info: FileChooserInfo,
    ) = textFieldWithBrowseButton(
      project,
      info.fileChooserTitle,
      info.fileChooserDescription,
      ExtendableTextField(10).apply {
        val fileChooserMacroFilter = info.fileChooserMacroFilter
        if (fileChooserMacroFilter != null) {
          MacrosDialog.addMacroSupport(this, fileChooserMacroFilter) { false }
        }
      },
      info.fileChooserDescriptor
    ) { getPresentablePath(it.path) }
  }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addThreadsFragment() =
    addLabeledTextSettingsEditorFragment(
      JBTextField(),
      object : LabeledSettingsFragmentInfo {
        override val editorLabel: String = MavenConfigurableBundle.message("maven.run.configuration.threads.label")
        override val settingsId: String = "maven.threads.fragment"
        override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.threads.name")
        override val settingsGroup: String = MavenConfigurableBundle.message("maven.run.configuration.general.options.group")
        override val settingsHint: String = MavenConfigurableBundle.message("maven.settings.general.thread.count.tooltip")
        override val settingsActionHint: String? = null
      },
      { generalSettingsOrDefault.threads },
      { generalSettingsOrDefault.threads = it }
    )
}