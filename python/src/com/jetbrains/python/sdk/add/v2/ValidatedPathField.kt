// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.BrowsableTargetEnvironmentType
import com.intellij.execution.target.TargetBrowserHints
import com.intellij.execution.target.getTargetType
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.observable.util.or
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.getParentOfType
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ComponentUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.components.ValidationType
import com.intellij.ui.dsl.builder.components.validationTooltip
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.asDisposable
import com.intellij.util.ui.AsyncProcessIcon
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.awt.event.ActionListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal interface PathValidator<T, P : PathHolder, VP : ValidatedPath<T, P>> {
  /**
   * [backProperty] should only be used in [PathValidator] and its inheritors
   */
  val backProperty: ObservableMutableProperty<VP?>
  val isDirtyValue: ObservableMutableProperty<Boolean>
  val isValidationInProgress: Boolean
  fun validate(input: String)
  fun markDirty() {
    isDirtyValue.set(true)
    backProperty.set(null)
  }

  val isValidationSuccessful: ObservableProperty<Boolean>
    get() = backProperty.transform { it?.validationResult?.successOrNull != null }
}

private interface ValidationStatusExtension

private class ValidationSuccessExtension<T>(val validationInfo: T) : ExtendableTextComponent.Extension, ValidationStatusExtension {
  override fun getIcon(hovered: Boolean): Icon = AllIcons.General.GreenCheckmark
  override fun getTooltip(): @NlsContexts.Tooltip String? {
    val tooltip = when (validationInfo) {
      is Unit -> null
      else -> validationInfo.toString().takeIf { it.isNotEmpty() }
    }
    return tooltip
  }
}

private object ValidationInProgressExtension : ExtendableTextComponent.Extension, ValidationStatusExtension {
  override fun getIcon(hovered: Boolean): Icon = AnimatedIcon.Default()
  override fun getTooltip(): @NlsContexts.Tooltip String {
    return message("python.add.sdk.wait.for.validation")
  }
}

@OptIn(FlowPreview::class, ExperimentalAtomicApi::class)
internal class ValidatedPathField<T, P : PathHolder, VP : ValidatedPath<T, P>>(
  val fileSystem: FileSystem<P>,
  val pathValidator: PathValidator<T, P, VP>,
  val canBeEdited: Boolean,
  browseFolderDialogTitle: @Nls String,
  isFileSelectionMode: Boolean,
) : TextFieldWithBrowseButton() {
  private lateinit var scope: CoroutineScope
  private val textInputFlow: MutableStateFlow<String?> = MutableStateFlow(null)

  /**
   * Single Back Property is shared across multiple forms,
   * only one of them should run a validation process after user input applied.
   * Editor Mode means the user changed the value and the value wasn't delivered from upstream.
   */
  private val editorMode = AtomicBoolean(false)

  /**
   * `true` once the value came from the user (a browse via [fieldAccessor] or typing), as opposed to an
   * autodetected fill (which writes [text] directly and leaves [editorMode] untouched). Used to persist
   * a tool executable path only when the user explicitly chose it.
   */
  val isUserEdited: Boolean get() = editorMode.load()

  private val validationAction = object : DumbAwareAction(AllIcons.Gutter.SuggestedRefactoringBulb) {
    fun doValidate() {
      if (!editorMode.load()) return

      pathValidator.validate(text.trim())
    }

    override fun actionPerformed(e: AnActionEvent) {
      doValidate()
    }
  }.apply {
    registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)), this@ValidatedPathField)
  }

  private val fieldAccessor = object : TextComponentAccessor<JTextField> {
    override fun getText(component: JTextField): @NlsSafe String {
      return component.text
    }

    override fun setText(component: JTextField, text: @NlsSafe String) {
      component.text = text
      editorMode.store(true)
      validationAction.doValidate()
    }
  }

  init {
    setButtonVisible(canBeEdited)
    isEnabled = canBeEdited
    addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        textInputFlow.value = text
      }
    })

    createBrowseFolderListener(browseFolderDialogTitle, isFileSelectionMode)?.also {
      addActionListener(it)
    }

    textField.addFocusListener(object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) {
        validationAction.doValidate()
      }
    })
  }

  private fun registerPropertyCallbacks() {
    pathValidator.backProperty.afterChange(scope.asDisposable()) { validatedPath ->
      if (validatedPath == null) {
        return@afterChange
      }

      if (validatedPath.pathHolder != null) {
        text = validatedPath.pathHolder.toString()
      }
      else {
        text = ""
      }
    }

    pathValidator.isDirtyValue.afterChange(scope.asDisposable()) { isDirtyValue ->
      with(textField as ExtendableTextComponent) {
        extensions
          .filter { it is ValidationStatusExtension }
          .forEach { removeExtension(it) }

        if (isDirtyValue) {
          if (pathValidator.isValidationInProgress) {
            isEnabled = false
            addExtension(ValidationInProgressExtension)
          }
        }
        else {
          editorMode.store(false)
          isEnabled = canBeEdited

          pathValidator.backProperty.get()?.validationResult?.let { validationResult ->
            validationResult
              .onSuccess {
                addExtension(ValidationSuccessExtension(it))
              }
          }
        }
      }
    }
  }

  fun initialize(scope: CoroutineScope) {
    this.scope = scope
    registerPropertyCallbacks()

    val rootPane = this.getParentOfType<JRootPane>()
    val topPanel = ComponentUtil.findParentByCondition(this) { it.parent !is JPanel }

    listOfNotNull(topPanel, rootPane).forEach { component ->
      component.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
          if (!this@ValidatedPathField.isVisible) return
          validationAction.doValidate()
        }
      })
    }


    scope.launch(Dispatchers.EDT) {
      textInputFlow
        .debounce(50) // setText method is a combination of two calls - remove + insert, should count them as 1
        .map {
          if (it == null) return@map null

          if (!editorMode.load() && (pathValidator.backProperty.get()?.pathHolder?.toString() ?: "") != it) {
            editorMode.store(true)
            pathValidator.markDirty()
          }

          it
        }
        .collect {}
    }
  }

  fun getFileChooserDescriptor(browseFolderDialogTitle: @Nls String, isFileSelection: Boolean): FileChooserDescriptor {
    val descriptor = if (isFileSelection) {
      FileChooserDescriptorFactory.singleFile()
    }
    else {
      FileChooserDescriptorFactory.singleDir()
    }

    descriptor
      .withShowHiddenFiles(SystemInfo.isUnix)
      .withTitle(browseFolderDialogTitle)

    // XXX: Workaround for PY-21787 and PY-43507 since the native macOS dialog always follows symlinks
    if (SystemInfo.isMac) {
      descriptor.isForcedToUseIdeaFileChooser = true
    }

    return descriptor
  }


  private fun createBrowseFolderListener(browseFolderDialogTitle: @Nls String, isFileSelectionMode: Boolean): ActionListener? {
    val descriptor = getFileChooserDescriptor(browseFolderDialogTitle, isFileSelectionMode)
    val targetBrowserHints = TargetBrowserHints(showLocalFsInBrowser = true, descriptor)
    val targetEnvironmentConfiguration = (fileSystem as? TargetFileSystem)?.targetEnvironmentConfiguration

    val listener = if (targetEnvironmentConfiguration == null) {
      BrowseFolderActionListener(this, null, descriptor, fieldAccessor)
    }
    else {
      val targetType = targetEnvironmentConfiguration.getTargetType()
      if (targetType is BrowsableTargetEnvironmentType) {
        targetType.createBrowser(
          ProjectManager.getInstance().defaultProject,
          browseFolderDialogTitle,
          fieldAccessor,
          this.textField,
          { targetEnvironmentConfiguration },
          targetBrowserHints
        )
      }
      else {
        null
      }
    }

    return listener
  }
}

/**
 * Shows [missingExecutableText] in place of the tool path field. Returns the tooltip component, so that the
 * caller can tell "hidden because this whole section is not selected" from "hidden because there is nothing
 * to show" - see the validation guard in [validatablePathField].
 */
private fun <T, P : PathHolder, V : ValidatedPath<T, P>> Panel.missingToolRow(
  fileSystem: FileSystem<*>,
  missingExecutableText: @Nls String,
  installAction: ActionLink?,
  validatedPathField: ValidatedPathField<T, P, V>,
  visiblePredicate: ObservableProperty<Boolean>,
): JPanel {
  val selectExecutableLink = if (fileSystem.isBrowsable && fileSystem.toolPathCanBePersisted) ActionLink(message("sdk.create.custom.select.executable.link")) {
    validatedPathField.button.doClick()
  }
  else null

  lateinit var tooltip: JPanel
  row("") {
    tooltip = validationTooltip(missingExecutableText,
                                installAction,
                                selectExecutableLink,
                                validationType = ValidationType.WARNING,
                                inline = true)
      .align(Align.FILL)
      .component
  }.visibleIf(visiblePredicate)

  return tooltip
}

internal fun <T, P : PathHolder, VP : ValidatedPath<T, P>> Panel.validatablePathField(
  fileSystem: FileSystem<P>,
  pathValidator: PathValidator<T, P, VP>,
  validationRequestor: DialogValidationRequestor,
  labelText: @Nls String,
  missingExecutableText: @Nls String?,
  installAction: ActionLink? = null,
  isFileSelectionMode: Boolean = true,
  venvExistenceValidationState: ObservableProperty<VenvExistenceValidationState>? = null,
  canBeEdited: Boolean = true,
): ValidatedPathField<T, P, VP> {

  val validatedPathField = ValidatedPathField(
    fileSystem = fileSystem,
    pathValidator = pathValidator,
    browseFolderDialogTitle = labelText,
    isFileSelectionMode = isFileSelectionMode,
    canBeEdited = canBeEdited,
  )

  /** A lookup has produced a verdict, as opposed to "nothing has been looked up yet". */
  val hasVerdict = pathValidator.backProperty.transform { it != null }

  /** No path resolved: either nothing was found, or nothing has been looked up yet. */
  val toolMissing = pathValidator.backProperty.transform { it?.pathHolder == null }

  val missingToolTooltip = if (missingExecutableText != null) {
    missingToolRow(
      fileSystem = fileSystem,
      missingExecutableText = missingExecutableText,
      installAction = if (canBeEdited) installAction else null,
      validatedPathField = validatedPathField,
      // Only claim that the tool is missing once we have actually looked for it.
      visiblePredicate = hasVerdict.and(toolMissing).and(pathValidator.isDirtyValue.not()),
    )
  }
  else null

  // The path field is hidden while the tool is being looked up (see below), and on a target that lookup is a
  // remote probe. The existing environment selector also hides its interpreter combo until the tool validates,
  // so without this row the whole section would render empty for the duration of the probe.
  val detectingIcon = if (!canBeEdited) {
    AsyncProcessIcon("$labelText detecting").also { icon ->
      row(labelText) {
        cell(icon).customize(UnscaledGaps(0))
        label(message("sdk.create.custom.tool.detecting"))
      }.visibleIf(hasVerdict.not() or pathValidator.isDirtyValue)
    }
  }
  else null

  val initialValidationRequestor = (validationRequestor
    and WHEN_PROPERTY_CHANGED(pathValidator.isDirtyValue)
    and WHEN_PROPERTY_CHANGED(pathValidator.backProperty))

  val finalValidationRequestor = if (venvExistenceValidationState != null) {
    initialValidationRequestor and WHEN_PROPERTY_CHANGED(venvExistenceValidationState)
  }
  else initialValidationRequestor

  val fieldRow = row(labelText) {
    cell(validatedPathField)
      .align(AlignX.FILL)
      .validationRequestor(finalValidationRequestor)
      .validationOnInput { component ->
        // This section is built for every environment manager and the unselected ones are hidden with
        // `rowsRange { }.visibleIf(..)`, so an invisible field usually means "another manager is selected" and
        // must not gate the dialog. But the field is also hidden when it has no path to show, and then the
        // detecting row or the missing tool warning stands in its place - keep validating in that case, or a
        // target without the tool installed would silently enable the action button.
        if (!component.isVisible && detectingIcon?.isVisible != true && missingToolTooltip?.isVisible != true) {
          return@validationOnInput null
        }

        val isVenvOverridden = when (venvExistenceValidationState?.get()) {
          is VenvExistenceValidationState.Warning -> true
          is VenvExistenceValidationState.Invisible, is VenvExistenceValidationState.Error, null -> false
        }

        if (isVenvOverridden) return@validationOnInput null

        val pyErrorMessage = pathValidator.backProperty.get()?.validationResult?.errorOrNull?.message

        when {
          pyErrorMessage != null -> {
            ValidationInfo(pyErrorMessage)
          }
          pathValidator.isValidationInProgress -> {
            ValidationInfo(message("python.add.sdk.wait.for.validation"))
          }
          pathValidator.isDirtyValue.get() -> {
            ValidationInfo(message("python.add.sdk.press.enter.to.validate")).asWarning()
          }
          else -> null
        }
      }
  }

  // A path field the user cannot edit and that has no path in it carries no information: there is no browse
  // button to pick another executable, and the missing tool row already says that the tool has to be installed
  // on the target. Once a path is resolved keep the field even if the version probe failed, so that the error
  // stays attached to something visible.
  if (!canBeEdited) {
    fieldRow.visibleIf(toolMissing.not())
  }

  return validatedPathField
}