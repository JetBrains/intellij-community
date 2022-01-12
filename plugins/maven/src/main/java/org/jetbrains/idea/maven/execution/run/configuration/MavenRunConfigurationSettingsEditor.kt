// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.run.configuration

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.diagnostic.logging.LogsGroupFragment
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.ui.*
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.ide.wizard.getCanonicalPath
import com.intellij.openapi.externalSystem.service.execution.configuration.*
import com.intellij.openapi.externalSystem.service.ui.getSelectedJdkReference
import com.intellij.openapi.externalSystem.service.ui.project.path.WorkingDirectoryField
import com.intellij.openapi.externalSystem.service.ui.properties.PropertiesFiled
import com.intellij.openapi.externalSystem.service.ui.properties.PropertiesInfo
import com.intellij.openapi.externalSystem.service.ui.properties.PropertiesTable
import com.intellij.openapi.externalSystem.service.ui.setSelectedJdkReference
import com.intellij.openapi.externalSystem.service.ui.util.LabeledSettingsFragmentInfo
import com.intellij.openapi.externalSystem.service.ui.util.PathFragmentInfo
import com.intellij.openapi.externalSystem.service.ui.util.SettingsFragmentInfo
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace
import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace.Companion.task
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel.Companion.createProjectJdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.openapi.roots.ui.distribution.DistributionComboBox
import com.intellij.openapi.roots.ui.distribution.FileChooserInfo
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.impl.CollapsibleTitledSeparator
import com.intellij.openapi.observable.util.lockOrSkip
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.execution.MavenRunnerSettings
import org.jetbrains.idea.maven.execution.RunnerBundle
import org.jetbrains.idea.maven.execution.run.configuration.MavenDistributionsInfo.Companion.asDistributionInfo
import org.jetbrains.idea.maven.execution.run.configuration.MavenDistributionsInfo.Companion.asMavenHome
import org.jetbrains.idea.maven.project.MavenConfigurableBundle
import org.jetbrains.idea.maven.project.MavenGeneralSettings
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.MavenWslUtil
import java.awt.Component
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
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
    val workingDirectoryField = addWorkingDirectoryFragment().component().component
    addCommandLineFragment(workingDirectoryField)
    addProfilesFragment(workingDirectoryField)
    addMavenOptionsGroupFragment()
    addJavaOptionsGroupFragment()
    add(LogsGroupFragment())
  }

  private val MavenRunConfiguration.generalSettingsOrDefault: MavenGeneralSettings
    get() = generalSettings ?: MavenProjectsManager.getInstance(project).generalSettings.clone()

  private val MavenRunConfiguration.runnerSettingsOrDefault: MavenRunnerSettings
    get() = runnerSettings ?: MavenRunner.getInstance(project).settings.clone()

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addMavenOptionsGroupFragment() =
    addOptionsGroup(
      "maven.general.options.group",
      MavenConfigurableBundle.message("maven.run.configuration.general.options.group.name"),
      MavenConfigurableBundle.message("maven.run.configuration.general.options.group"),
      MavenProjectBundle.message("configurable.MavenSettings.display.name"),
      { generalSettings },
      { generalSettingsOrDefault },
      { generalSettings = it }
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
      addMultiProjectBuildPolicyTag()
    }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addJavaOptionsGroupFragment() =
    addOptionsGroup(
      "maven.runner.options.group",
      MavenConfigurableBundle.message("maven.run.configuration.runner.options.group.name"),
      MavenConfigurableBundle.message("maven.run.configuration.runner.options.group"),
      RunnerBundle.message("maven.tab.runner"),
      { runnerSettings },
      { runnerSettingsOrDefault },
      { runnerSettings = it }
    ) {
      addJreFragment()
      addEnvironmentFragment()
      addVmOptionsFragment()
      addPropertiesFragment()
      addSkipTestsTag()
      addResolveWorkspaceArtifactsTag()
    }

  private fun <S : FragmentedSettings, Settings> SettingsFragmentsContainer<S>.addOptionsGroup(
    id: String,
    name: @Nls(capitalization = Nls.Capitalization.Sentence) String,
    group: @Nls(capitalization = Nls.Capitalization.Title) String,
    settingsName: @NlsContexts.ConfigurableName String,
    getSettings: S.() -> Settings?,
    getDefaultSettings: S.() -> Settings,
    setSettings: S.(Settings?) -> Unit,
    configure: SettingsFragmentsContainer<S>.() -> Unit
  ) = add(object : NestedGroupFragment<S>(id, name, group, { true }) {

    private val separator = CollapsibleTitledSeparator(group)
    private val checkBox: JCheckBox
    private val checkBoxWithLink: JComponent

    init {
      val labelText = MavenConfigurableBundle.message("maven.run.configuration.options.group.inherit")
      @Suppress("HardCodedStringLiteral") val leadingLabelText = labelText.substringBefore("<a>")
      @Suppress("HardCodedStringLiteral") val linkLabelText = labelText.substringAfter("<a>").substringBefore("</a>")
      @Suppress("HardCodedStringLiteral") val trailingLabelText = labelText.substringAfter("</a>")
      checkBox = JCheckBox(leadingLabelText)
      checkBoxWithLink = JPanel().apply {
        layout = HorizontalLayout(0)
        add(checkBox)
        add(ActionLink(linkLabelText) {
          val showSettingsUtil = ShowSettingsUtil.getInstance()
          showSettingsUtil.showSettingsDialog(project, settingsName)
        })
        add(JLabel(trailingLabelText))
      }
    }

    override fun createChildren() = SettingsFragmentsContainer.fragments<S> {
      addSettingsEditorFragment(
        checkBoxWithLink,
        object : SettingsFragmentInfo {
          override val settingsId: String = "$id.checkbox"
          override val settingsName: String? = null
          override val settingsGroup: String? = null
          override val settingsPriority: Int = 0
          override val settingsType = SettingsEditorFragmentType.EDITOR
          override val settingsHint: String? = null
          override val settingsActionHint: String? = null
        },
        { it, _ -> checkBox.isSelected = it.getSettings() == null },
        { it, _ -> it.setSettings(if (checkBox.isSelected) null else (it.getSettings() ?: it.getDefaultSettings())) }
      )
      for (fragment in SettingsFragmentsContainer.fragments(configure)) {
        bind(checkBox, fragment)
        add(fragment)
      }
    }

    override fun getBuilder() = object : FragmentedSettingsBuilder<S>(children, this, this) {

      override fun createHeaderSeparator() = separator

      override fun addLine(component: Component, top: Int, left: Int, bottom: Int) {
        if (component === checkBoxWithLink) {
          super.addLine(component, top, left, bottom + TOP_INSET)
          myGroupInset += LEFT_INSET
        }
        else {
          super.addLine(component, top, left, bottom)
        }
      }

      init {
        children.forEach { bind(separator, it) }
        resetOperation.afterOperation {
          separator.expanded = !checkBox.isSelected
        }
      }
    }
  }).apply { isRemovable = false }

  private fun bind(checkBox: JCheckBox, fragment: SettingsEditorFragment<*, *>) {
    checkBox.addItemListener {
      val component = fragment.component()
      if (component != null) {
        UIUtil.setEnabledRecursively(component, !checkBox.isSelected)
      }
    }
    fragment.addSettingsEditorListener {
      if (resetOperation.isOperationCompleted()) {
        checkBox.isSelected = false
      }
    }
  }

  private fun bind(separator: CollapsibleTitledSeparator, fragment: SettingsEditorFragment<*, *>) {
    val mutex = AtomicBoolean()
    separator.onAction {
      mutex.lockOrSkip {
        fragment.component.isVisible = fragment.isSelected && separator.expanded
        fragment.hintComponent?.isVisible = fragment.isSelected && separator.expanded
      }
    }
    fragment.addSettingsEditorListener {
      mutex.lockOrSkip {
        if (resetOperation.isOperationCompleted()) {
          separator.expanded = true
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
    ).modifyLabeledComponentSize { columns(10) }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addMultiProjectBuildPolicyTag() {
    addVariantTag(
      "maven.multi.project.build.policy.tag",
      MavenConfigurableBundle.message("maven.run.configuration.multi.project.build.policy"),
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
    workingDirectoryField: WorkingDirectoryField
  ) = addCommandLineFragment(
    project,
    MavenCommandLineInfo(project, workingDirectoryField),
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
        addRemovableLabeledSettingsEditorFragment(
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
          { runnerSettingsOrDefault.jreName },
          { runnerSettingsOrDefault.setJreName(it) },
          { MavenRunnerSettings.USE_PROJECT_JDK }
        )
      }

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addPropertiesFragment() =
    addLabeledSettingsEditorFragment(
      PropertiesFiled(project, object : PropertiesInfo {
        override val dialogTitle: String = MavenConfigurableBundle.message("maven.run.configuration.properties.dialog.title")
        override val dialogTooltip: String = MavenConfigurableBundle.message("maven.run.configuration.properties.dialog.tooltip")
        override val dialogLabel: String = MavenConfigurableBundle.message("maven.run.configuration.properties.dialog.label")
        override val dialogEmptyState: String = MavenConfigurableBundle.message("maven.run.configuration.properties.dialog.empty.state")
        override val dialogOkButton: String = MavenConfigurableBundle.message("maven.run.configuration.properties.dialog.ok.button")
      }),
      object : LabeledSettingsFragmentInfo {
        override val editorLabel: String = MavenConfigurableBundle.message("maven.run.configuration.properties.label")
        override val settingsId: String = "maven.properties.fragment"
        override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.properties.name")
        override val settingsGroup: String = MavenConfigurableBundle.message("maven.run.configuration.runner.options.group")
        override val settingsHint: String? = null
        override val settingsActionHint: String? = null
      },
      { it, c -> c.properties = it.runnerSettingsOrDefault.mavenProperties.map { PropertiesTable.Property(it.key, it.value) } },
      { it, c -> it.runnerSettingsOrDefault.mavenProperties = c.properties.associate { it.name to it.value } },
      { it.runnerSettingsOrDefault.mavenProperties.isNotEmpty() }
    )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addProfilesFragment(
    workingDirectoryField: WorkingDirectoryField
  ) = addLabeledSettingsEditorFragment(
    MavenProfilesFiled(project, workingDirectoryField),
    object : LabeledSettingsFragmentInfo {
      override val editorLabel: String = MavenConfigurableBundle.message("maven.run.configuration.profiles.label")

      override val settingsId: String = "maven.profiles.fragment"
      override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.profiles.name")
      override val settingsGroup: String? = null
      override val settingsHint: String = MavenConfigurableBundle.message("maven.run.configuration.profiles.hint")
      override val settingsActionHint: String? = null
    },
    { it, c -> c.profiles = it.runnerParameters.profilesMap },
    { it, c -> it.runnerParameters.profilesMap = c.profiles }
  )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addUserSettingsFragment() =
    addPathFragment(
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
    userSettingsComponent: TextFieldWithBrowseButton
  ) = addPathFragment(
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
      val userSettingsPath = getCanonicalPath(userSettingsComponent.text.trim())
      val userSettingsFile = MavenWslUtil.getUserSettings(project, userSettingsPath, mavenConfig)
      val userSettings = getCanonicalPath(userSettingsFile.path)
      val localRepository = MavenWslUtil.getLocalRepo(project, "", distribution, userSettings, mavenConfig)
      getCanonicalPath(localRepository.path)
    }
  )

  private fun SettingsFragmentsContainer<MavenRunConfiguration>.addThreadsFragment() =
    addRemovableLabeledTextSettingsEditorFragment(
      JBTextField(),
      object : LabeledSettingsFragmentInfo {
        override val editorLabel: String = MavenConfigurableBundle.message("maven.run.configuration.threads.label")
        override val settingsId: String = "maven.threads.fragment"
        override val settingsName: String = MavenConfigurableBundle.message("maven.run.configuration.threads.name")
        override val settingsGroup: String = MavenConfigurableBundle.message("maven.run.configuration.general.options.group")
        override val settingsHint: String? = null
        override val settingsActionHint: String = MavenConfigurableBundle.message("maven.settings.general.thread.count.tooltip")
      },
      { generalSettingsOrDefault.threads },
      { generalSettingsOrDefault.threads = it }
    ).modifyLabeledComponentSize { columns(10) }
}