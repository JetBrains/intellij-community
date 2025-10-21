// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.BrowsableTargetEnvironmentType
import com.intellij.execution.target.TargetBrowserHints
import com.intellij.execution.target.getTargetType
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.components.ValidationType
import com.intellij.ui.dsl.builder.components.validationTooltip
import com.intellij.util.asDisposable
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.onFailure
import com.jetbrains.python.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Icon
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.cos

interface PathValidator<T, P : PathHolder, VP : ValidatedPath<T, P>> {
  val backProperty: ObservableMutableProperty<VP?>
  val isDirtyValue: ObservableMutableProperty<Boolean>
  val isValidationInProgress: Boolean
  fun validate(input: String)
  fun markDirty() {
    isDirtyValue.set(true)
    backProperty.set(null)
  }
}

private class ValidationSuccessExtension<T>(val validationInfo: T) : ExtendableTextComponent.Extension {
  override fun getIcon(hovered: Boolean): Icon = AllIcons.General.GreenCheckmark
  override fun getTooltip(): @NlsContexts.Tooltip String? {
    val tooltip = when (validationInfo) {
      is Unit -> null
      else -> validationInfo.toString().takeIf { it.isNotEmpty() }
    }
    return tooltip
  }
}

private object ValidationErrorExtension : ExtendableTextComponent.Extension {
  override fun getIcon(hovered: Boolean): Icon = AllIcons.Status.FailedInProgress
}

private object ValidationInProgressExtension : ExtendableTextComponent.Extension {
  override fun getIcon(hovered: Boolean): Icon = AnimatedIcon.Default()
  override fun getTooltip(): @NlsContexts.Tooltip String {
    return message("python.add.sdk.wait.for.validation")
  }
}

private class DebounceCounterIcon(val icon: Icon, val period: Int) : Icon {
  private val time: AtomicLong = AtomicLong(System.currentTimeMillis())

  fun reset() {
    time.set(System.currentTimeMillis())
  }

  override fun getIconWidth(): Int {
    return icon.iconWidth
  }

  override fun getIconHeight(): Int {
    return icon.iconHeight
  }

  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
    assert(period > 0) { "unexpected" }
    val time = (System.currentTimeMillis() - this.time.get()) % period
    val alpha = ((cos(2 * Math.PI * time / period) + 1) / 2).toFloat()
    if (alpha > 0) {
      if (alpha < 1 && g is Graphics2D) {
        val g2d = g.create() as Graphics2D
        try {
          g2d.composite = AlphaComposite.SrcAtop.derive(alpha)
          icon.paintIcon(c, g2d, x, y)
        }
        finally {
          g2d.dispose()
        }
      }
      else {
        icon.paintIcon(c, g, x, y)
      }
    }
  }
}

private class AnimatedFadingIcon(private val icon: DebounceCounterIcon) : AnimatedIcon(50, icon) {
  fun reset() {
    icon.reset()
  }

  companion object {
    fun build(icon: Icon, period: Int = VALIDATION_DELAY * 2): AnimatedFadingIcon {
      return AnimatedFadingIcon(DebounceCounterIcon(icon, period))
    }
  }
}

private const val VALIDATION_DELAY = 2000

@OptIn(FlowPreview::class, ExperimentalAtomicApi::class)
internal class ValidatedPathField<T, P : PathHolder, VP : ValidatedPath<T, P>>(
  val fileSystem: FileSystem<P>,
  val pathValidator: PathValidator<T, P, VP>,
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

  private val validationAction = object : DumbAwareAction(AllIcons.Gutter.SuggestedRefactoringBulb) {
    fun doValidate() {
      if (!editorMode.load()) return

      pathValidator.validate(text.trim())
    }

    override fun actionPerformed(e: AnActionEvent) {
      with(textField as ExtendableTextComponent) {
        if (extensions.contains(validationWaitExtension)) {
          doValidate()
        }
      }
    }
  }.apply {
    registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)), this@ValidatedPathField)
  }

  private val validationWaitIcon = AnimatedFadingIcon.build(AllIcons.Gutter.SuggestedRefactoringBulb)
  private val validationWaitExtension: ExtendableTextComponent.Extension = ExtendableTextComponent.Extension.create(
    validationWaitIcon, message("python.add.sdk.wait.for.validation")
  ) {
    validationAction.doValidate()
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

  private val browseFolderActionLister: ActionListener? = createBrowseFolderListener(browseFolderDialogTitle, isFileSelectionMode)?.also {
    addActionListener(it)
  }

  init {
    addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        textInputFlow.value = text
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
        extensions.forEach { removeExtension(it) }

        if (isDirtyValue) {
          if (pathValidator.isValidationInProgress) {
            isEnabled = false
            addExtension(ValidationInProgressExtension)
          }
          else {
            addExtension(validationWaitExtension)
          }
        }
        else {
          editorMode.store(false)
          browseFolderActionLister?.let { setButtonVisible(true) }
          isEnabled = true

          pathValidator.backProperty.get()?.validationResult?.let { validationResult ->
            validationResult
              .onFailure {
                addExtension(ValidationErrorExtension)
              }
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

    scope.launch {
      textInputFlow
        .debounce(50) // setText method is a combination of two calls - remove + insert, should count them as 1
        .map {
          if (it == null) return@map null

          if (editorMode.load()) {
            validationWaitIcon.reset()
          }
          else if ((pathValidator.backProperty.get()?.pathHolder?.toString() ?: "") != it) {
            editorMode.store(true)
            pathValidator.markDirty()
          }

          it
        }
        .debounce(VALIDATION_DELAY.toLong())
        .collectLatest {
          if (it == null) return@collectLatest
          validationAction.doValidate()
        }
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
    val targetEnvironmentConfiguration = (fileSystem as? FileSystem.Target)?.targetEnvironmentConfiguration

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

private fun <T, P : PathHolder, V : ValidatedPath<T, P>> Panel.installToolRow(
  fileSystem: FileSystem<*>,
  missingExecutableText: @Nls String,
  installAction: ActionLink,
  validatedPathField: ValidatedPathField<T, P, V>,
): Row {
  val selectExecutableLink = if (fileSystem.isBrowseable) ActionLink(message("sdk.create.custom.select.executable.link")) {
    validatedPathField.button.doClick()
  }
  else null

  return row("") {
    validationTooltip(missingExecutableText,
                      installAction,
                      selectExecutableLink,
                      validationType = ValidationType.WARNING,
                      inline = true)
      .align(Align.FILL)
      .component
  }
}

internal fun <T, P : PathHolder, VP : ValidatedPath<T, P>> Panel.validatablePathField(
  fileSystem: FileSystem<P>,
  pathValidator: PathValidator<T, P, VP>,
  validationRequestor: DialogValidationRequestor,
  labelText: @Nls String,
  missingExecutableText: @Nls String?,
  installAction: ActionLink? = null,
  isFileSelectionMode: Boolean = true,
): ValidatedPathField<T, P, VP> {

  val validatedPathField = ValidatedPathField(
    fileSystem = fileSystem,
    pathValidator = pathValidator,
    browseFolderDialogTitle = labelText,
    isFileSelectionMode = isFileSelectionMode,
  )

  if (missingExecutableText != null && installAction != null && !fileSystem.isReadOnly) {
    installToolRow(
      fileSystem = fileSystem,
      missingExecutableText = missingExecutableText,
      installAction = installAction,
      validatedPathField = validatedPathField
    ).visibleIf(pathValidator.backProperty.transform { it?.pathHolder == null }.and(pathValidator.isDirtyValue.not()))
  }

  row(labelText) {
    cell(validatedPathField)
      .align(AlignX.FILL)
      .validationRequestor(validationRequestor
                             and WHEN_PROPERTY_CHANGED(pathValidator.isDirtyValue)
                             and WHEN_PROPERTY_CHANGED(pathValidator.backProperty)
      )
      .validationOnInput { component ->
        if (!component.isVisible) return@validationOnInput null

        val pyErrorMessage = pathValidator.backProperty.get()?.validationResult?.errorOrNull?.message

        when {
          pyErrorMessage != null -> {
            ValidationInfo(pyErrorMessage)
          }
          pathValidator.isDirtyValue.get() -> {
            ValidationInfo(message("python.add.sdk.wait.for.validation"))
          }
          else -> null
        }
      }
  }

  return validatedPathField
}