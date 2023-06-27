// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black.configuration

import com.intellij.codeInsight.AutoPopupController
import com.intellij.icons.AllIcons
import com.intellij.ide.actionsOnSave.*
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import com.intellij.ui.dsl.builder.*
import com.intellij.util.io.await
import com.intellij.util.text.nullize
import com.intellij.util.textCompletion.TextCompletionUtil
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.UIUtil
import com.intellij.webcore.packaging.PackageManagementService
import com.jetbrains.python.PyBundle
import com.jetbrains.python.black.BlackFormatterUtil
import com.jetbrains.python.black.BlackFormatterVersionService
import com.jetbrains.python.black.configuration.BlackFormatterConfiguration.BlackFormatterOption.Companion.toCliOptionFlags
import com.jetbrains.python.newProject.steps.createPythonSdkComboBox
import com.jetbrains.python.packaging.PyPackageManagers
import com.jetbrains.python.packaging.PyPackagesNotificationPanel
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.io.File
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel

const val CONFIGURABLE_ID = "com.jetbrains.python.black.configuration.BlackFormatterConfigurable"

class BlackFormatterConfigurable(val project: Project) : BoundConfigurable(PyBundle.message("black.configurable.name")) {

  private var storedState: BlackFormatterConfiguration

  private var isBlackFormatterPackageInstalled: Boolean = false
  private var detectedBlackExecutable: File? = null
  private var selectedSdk: Sdk? = null
  private var isLocalSdk = false

  private lateinit var enableOnReformatCheckBox: JCheckBox
  private lateinit var enableOnSaveCheckBox: JCheckBox
  private lateinit var packageNotInstalledErrorLabel: JLabel
  private lateinit var remoteSdkErrorLabel: JLabel
  private lateinit var installButton: JButton
  private lateinit var installPanel: Panel
  private lateinit var settingsPanel: Panel
  private lateinit var pathToBinaryRow: Row
  private lateinit var sdkSelectionRow: Row
  private lateinit var cliArgumentsRow: Row
  private lateinit var executionModeComboBox: ComboBox<BlackFormatterConfiguration.ExecutionMode>

  private val blackExecutablePathField = TextFieldWithBrowseButton().apply {
    addBrowseFolderListener(
      @Suppress("DialogTitleCapitalization")
      PyBundle.message("black.select.path.to.executable"),
      null,
      project,
      FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
    )
  }

  private val sdkSelectionComboBox = createPythonSdkComboBox(project.modules.mapNotNull { it.pythonSdk }, null)

  private val cliArgumentsTextField = BlackTextFieldWithAutoCompletion(project, object :
    TextFieldWithAutoCompletionListProvider<BlackFormatterConfiguration.CliOptionFlag>(
      BlackFormatterConfiguration.options.toCliOptionFlags()) {

    override fun getLookupString(item: BlackFormatterConfiguration.CliOptionFlag): String = item.flag + " "

    override fun getTailText(item: BlackFormatterConfiguration.CliOptionFlag): String? = item.option.param

    override fun getTypeText(item: BlackFormatterConfiguration.CliOptionFlag): String = item.description()
  })

  var mainPanel: DialogPanel = panel {
    row {
      label(PyBundle.message("black.execution.mode.label"))
        .applyToComponent { toolTipText = PyBundle.message("black.execution.mode.tooltip.text") }
        .applyToComponent { icon = AllIcons.General.ContextHelp }
      executionModeComboBox = comboBox(EnumComboBoxModel(
        BlackFormatterConfiguration.ExecutionMode::class.java))
        .applyToComponent { renderer = executionModeComboBoxRenderer }
        .component
      layout(RowLayout.LABEL_ALIGNED)
    }
    row {
      remoteSdkErrorLabel = label(PyBundle.message("black.remote.sdk.error"))
        .applyToComponent { icon = AllIcons.General.Warning }
        .visible(false)
        .component
    }
    pathToBinaryRow = row(PyBundle.message("black.executable.label")) {
      layout(RowLayout.LABEL_ALIGNED)
      cell(blackExecutablePathField)
        .validationInfo { blackExecutableValidationInfo() }
        .onChanged { updateUiState() }
        .align(AlignX.FILL)
      bottomGap(BottomGap.SMALL)
    }
    sdkSelectionRow = row(PyBundle.message("black.sdk.selection.combobox.label")) {
      layout(RowLayout.LABEL_ALIGNED)
      cell(sdkSelectionComboBox)
        .resizableColumn()
        .align(AlignX.FILL)
        .columns(COLUMNS_SHORT)
    }
    installPanel = panel {
      row {
        packageNotInstalledErrorLabel = label(PyBundle.message("black.not.installed.error"))
          .applyToComponent { icon = AllIcons.General.Warning }
          .component
        installButton = button(PyBundle.message("black.install.button.label")) {
          runBlockingModal(project, PyBundle.message("black.installing.modal.title")) {
            withContext(Dispatchers.EDT) {
              if (selectedSdk != null) {
                val errorDescription = installBlackFormatter(selectedSdk!!)
                if (errorDescription == null) {
                  isBlackFormatterPackageInstalled = true
                  enableOnReformatCheckBox.isSelected = true
                  updateUiState()
                }
                else {
                  PyPackagesNotificationPanel
                    .showPackageInstallationError(@Suppress("DialogTitleCapitalization")
                                                  PyBundle.message("black.installation.error.title"),
                                                  errorDescription)
                }
              }
            }
          }
        }.component
      }
    }
    settingsPanel = panel {
      row(PyBundle.message("black.use.section.label")) {
        layout(RowLayout.LABEL_ALIGNED)
        enableOnReformatCheckBox = checkBox(PyBundle.message("black.enable.black.checkbox.label")).component
        val shortcut = ActionManager.getInstance().getKeyboardShortcut(IdeActions.ACTION_EDITOR_REFORMAT)
        shortcut?.let { comment(KeymapUtil.getShortcutText(it)) }
      }
      row {
        label("")
        layout(RowLayout.LABEL_ALIGNED)
        bottomGap(BottomGap.SMALL)
        enableOnSaveCheckBox = checkBox(PyBundle.message("black.enable.action.on.save.label")).component
        val link = ActionsOnSaveConfigurable.createGoToActionsOnSavePageLink()
        cell(link)
      }
      cliArgumentsRow = row(PyBundle.message("black.cli.args.text.field.label")) {
        cell(cliArgumentsTextField)
          .resizableColumn()
          .align(AlignX.FILL)
          .applyToComponent {
            background = UIUtil.getTextFieldBackground()
          }
          .comment(PyBundle.message("black.cli.args.comment"), MAX_LINE_LENGTH_WORD_WRAP)
      }
    }
  }

  init {
    storedState = BlackFormatterConfiguration.getBlackConfiguration(project)

    selectedSdk = storedState.getSdk(project)
                  ?: if (project.modules.size == 1) project.pythonSdk
                  else null

    updateSdkInfo()

    detectedBlackExecutable = BlackFormatterUtil.detectBlackExecutable()

    executionModeComboBox.addActionListener { updateUiState() }

    sdkSelectionComboBox.addActionListener {
      selectedSdk = sdkSelectionComboBox.item
      updateSdkInfo()
      updateUiState()
    }
  }

  private fun initForm() {
    enableOnReformatCheckBox.isSelected = storedState.enabledOnReformat
    enableOnSaveCheckBox.isSelected = storedState.enabledOnSave
    executionModeComboBox.item = storedState.executionMode
    cliArgumentsTextField.text = storedState.cmdArguments ?: ""
    sdkSelectionComboBox.item = selectedSdk

    blackExecutablePathField.emptyText.text = getBlackExecPathPlaceholderMessage()
    storedState.pathToExecutable?.let {
      blackExecutablePathField.text = it
    }

    if (storedState == BlackFormatterConfiguration()) {
      if (isBlackFormatterPackageInstalled) {
        executionModeComboBox.selectedItem = BlackFormatterConfiguration.ExecutionMode.PACKAGE
      }
      else if (detectedBlackExecutable != null) {
        executionModeComboBox.selectedItem = BlackFormatterConfiguration.ExecutionMode.BINARY
      }
    }

    updateUiState()
  }

  private fun updateUiState() {
    val isBinaryMode = executionModeComboBox.selectedItem == BlackFormatterConfiguration.ExecutionMode.BINARY

    installPanel.visible(!isBlackFormatterPackageInstalled && !isBinaryMode && isLocalSdk)
    pathToBinaryRow.visible(isBinaryMode)
    sdkSelectionRow.visible(!isBinaryMode)

    if (selectedSdk == null) {
      packageNotInstalledErrorLabel.text = PyBundle.message("black.no.project.interpreter.error")
      installButton.isVisible = false
    }
    else if (!isLocalSdk) {
      remoteSdkErrorLabel.isVisible = executionModeComboBox.selectedItem == BlackFormatterConfiguration.ExecutionMode.PACKAGE
    }

    val canBeEnabled = canBeEnabled()
    settingsPanel.enabled(canBeEnabled)
    enableOnReformatCheckBox.isSelected = storedState.enabledOnReformat && canBeEnabled
    enableOnSaveCheckBox.isSelected = storedState.enabledOnSave && canBeEnabled
  }

  private fun updateSdkInfo() {
    isLocalSdk = selectedSdk?.let { it.sdkType.isLocalSdk(it) } ?: false
    isBlackFormatterPackageInstalled = BlackFormatterUtil.isBlackFormatterInstalledOnProjectSdk(selectedSdk)
  }

  private suspend fun installBlackFormatter(sdk: Sdk): PackageManagementService.ErrorDescription? {
    val manager = PyPackageManagers.getInstance().getManagementService(project, sdk)
    val blackPackage = manager.allPackagesCached.firstOrNull { pyPackage -> pyPackage.name == BlackFormatterUtil.PACKAGE_NAME }
    val result = CompletableFuture<PackageManagementService.ErrorDescription>()
    val listener = object : PackageManagementService.Listener {
      override fun operationStarted(packageName: String?) {}

      override fun operationFinished(packageName: String?, errorDescription: PackageManagementService.ErrorDescription?) {
        if (errorDescription == null) {
          result.complete(null)
        }
        else {
          result.complete(errorDescription)
        }
      }
    }
    manager.installPackage(blackPackage, null, false, null, listener, false)
    return result.await()
  }

  private fun canBeEnabled(): Boolean {
    return when (executionModeComboBox.selectedItem) {
      BlackFormatterConfiguration.ExecutionMode.BINARY ->
        detectedBlackExecutable != null || storedState.pathToExecutable != null || blackExecutableValidationInfo() == null
      BlackFormatterConfiguration.ExecutionMode.PACKAGE ->
        selectedSdk != null && isLocalSdk && isBlackFormatterPackageInstalled
      else -> false
    }
  }

  private fun applyToConfig(configuration: BlackFormatterConfiguration): BlackFormatterConfiguration = configuration.apply {
    executionMode = executionModeComboBox.item
    enabledOnReformat = enableOnReformatCheckBox.isSelected
    enabledOnSave = enableOnSaveCheckBox.isSelected
    cmdArguments = cliArgumentsTextField.text
    sdkUUID = (sdkSelectionComboBox.item?.sdkAdditionalData as? PythonSdkAdditionalData)?.uuid?.toString()

    pathToExecutable = if (blackExecutableValidationInfo() == null) {
      blackExecutablePathField.text.nullize() ?: BlackFormatterUtil.detectBlackExecutable()?.absolutePath
    }
    else null
  }

  private fun blackExecutableValidationInfo(): ValidationInfo? =
    BlackFormatterUtil.validateBlackExecutable(
      blackExecutablePathField.text.nullize() ?: BlackFormatterUtil.detectBlackExecutable()?.absolutePath)

  private fun getBlackExecPathPlaceholderMessage(): String {
    return BlackFormatterUtil.detectBlackExecutable()?.let {
      PyBundle.message("black.executable.auto.detected.path", it.absolutePath)
    } ?: PyBundle.message("black.executable.not.found", if (SystemInfo.isWindows) 0 else 1)
  }

  override fun isModified(): Boolean = storedState != applyToConfig(storedState.copy())

  override fun reset() {
    initForm()
    updateUiState()
  }

  override fun apply() {
    applyToConfig(storedState)
  }
  override fun createPanel(): DialogPanel {
    mainPanel.registerValidators(disposable!!)
    return mainPanel
  }

  companion object {
    private val executionModeComboBoxRenderer =
      SimpleListCellRenderer.create<BlackFormatterConfiguration.ExecutionMode>(
        SimpleListCellRenderer.Customizer { label, value, _ ->
          val text: @Nls String = when (value) {
            BlackFormatterConfiguration.ExecutionMode.PACKAGE -> PyBundle.message("black.execution.mode.package")
            BlackFormatterConfiguration.ExecutionMode.BINARY -> PyBundle.message("black.execution.mode.binary")
            null -> ""
          }
          label.text = text
        })
  }


  class BlackTextFieldWithAutoCompletion(project: Project,
                                         provider: TextFieldWithAutoCompletionListProvider<BlackFormatterConfiguration.CliOptionFlag>)
    : TextFieldWithCompletion(project, provider, "", true, true, true) {
    override fun createEditor(): EditorEx {
      val editor = super.createEditor()
      val disableSpellChecking = SpellCheckingEditorCustomizationProvider.getInstance().disabledCustomization
      disableSpellChecking?.customize(editor)
      editor.putUserData(AutoPopupController.ALWAYS_AUTO_POPUP, true)
      val completionShortcut = KeymapUtil.getFirstKeyboardShortcutText(
        ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION))
      if (completionShortcut.isNotEmpty()) {
        TextCompletionUtil.installCompletionHint(editor)
      }
      return editor
    }

    override fun getText(): String = super.getText().trimEnd()
  }

  class BlackFormatterActionOnSaveInfoProvider : ActionOnSaveInfoProvider() {
    override fun getActionOnSaveInfos(context: ActionOnSaveContext):
      List<ActionOnSaveInfo> = listOf(BlackFormatterActionOnSaveInfo(context))

    override fun getSearchableOptions(): Collection<String> {
      return listOf(PyBundle.message("black.action.on.save.name"), PyBundle.message("black.configurable.name"))
    }
  }

  internal class BlackFormatterActionOnSaveInfo(actionOnSaveContext: ActionOnSaveContext)
    : ActionOnSaveBackedByOwnConfigurable<BlackFormatterConfigurable>(actionOnSaveContext, CONFIGURABLE_ID,
                                                                      BlackFormatterConfigurable::class.java) {

    override fun setActionOnSaveEnabled(configurable: BlackFormatterConfigurable, enabled: Boolean) {
      configurable.enableOnSaveCheckBox.isSelected = enabled
    }

    override fun getCommentAccordingToStoredState() =
      getCommentForBlack(BlackFormatterConfiguration.getBlackConfiguration(project))

    override fun getCommentAccordingToUiState(configurable: BlackFormatterConfigurable) =
      getCommentForBlack(configurable.storedState)

    private fun getCommentForBlack(configuration: BlackFormatterConfiguration): ActionOnSaveComment {
      val version = runBlockingModal(project, "") {
        BlackFormatterVersionService.getVersion(project)
      }
      return when (configuration.executionMode) {
        BlackFormatterConfiguration.ExecutionMode.BINARY -> {
          configuration.pathToExecutable?.let {
            ActionOnSaveComment.info(PyBundle.message("black.action.on.save.executable.info", version, it))
          } ?: ActionOnSaveComment.warning(PyBundle.message("black.action.on.save.executable.path.not.specified"))
        }
        BlackFormatterConfiguration.ExecutionMode.PACKAGE -> {
          if (version != BlackFormatterVersionService.UNKNOWN_VERSION) {
            ActionOnSaveComment.info(PyBundle.message("black.action.on.save.package.info", version))
          }
          else {
            ActionOnSaveComment.warning(PyBundle.message("black.not.installed.error"))
          }
        }
      }
    }

    override fun isActionOnSaveEnabledAccordingToUiState(configurable: BlackFormatterConfigurable): Boolean {
      return configurable.enableOnSaveCheckBox.isSelected
    }

    override fun isActionOnSaveEnabledAccordingToStoredState(): Boolean =
      BlackFormatterConfiguration.getBlackConfiguration(project).enabledOnSave

    override fun getActionOnSaveName(): String = PyBundle.message("black.action.on.save.name")

    override fun isApplicableAccordingToUiState(configurable: BlackFormatterConfigurable) =
      when (configurable.storedState.executionMode) {
        BlackFormatterConfiguration.ExecutionMode.PACKAGE ->
          BlackFormatterUtil.isBlackFormatterInstalledOnProjectSdk(configurable.selectedSdk)
        BlackFormatterConfiguration.ExecutionMode.BINARY ->
          BlackFormatterUtil.isBlackExecutableDetected()
      }

    override fun isApplicableAccordingToStoredState(): Boolean {
      val configuration = BlackFormatterConfiguration.getBlackConfiguration(project)
      return when (configuration.executionMode) {
        BlackFormatterConfiguration.ExecutionMode.PACKAGE ->
          BlackFormatterUtil.isBlackFormatterInstalledOnProjectSdk(configuration.getSdk(project))
        BlackFormatterConfiguration.ExecutionMode.BINARY ->
          BlackFormatterUtil.isBlackExecutableDetected()
      }
    }

    override fun getActionLinks() = listOf(createGoToPageInSettingsLink(CONFIGURABLE_ID))
  }
}