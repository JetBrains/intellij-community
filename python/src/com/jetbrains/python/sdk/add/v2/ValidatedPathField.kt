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
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.and
import com.intellij.openapi.observable.util.isNull
import com.intellij.openapi.observable.util.not
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
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
import kotlin.time.Duration.Companion.minutes

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
class ValidatedPathField<T, P : PathHolder, V : ValidatedPath<T, P>>(
  val fileSystem: FileSystem<P>,
  val backProperty: ObservableMutableProperty<V?>,
  browseFolderDialogTitle: @Nls String,
  isFileSelectionMode: Boolean,
  val isValidationActiveProperty: ObservableMutableProperty<Boolean>,
  val pathValidator: suspend (P) -> V,
) : TextFieldWithBrowseButton() {
  private lateinit var scope: CoroutineScope
  private val textInputFlow: MutableStateFlow<String?> = MutableStateFlow(null)
  private var validationJob: Deferred<Unit>? = null

  /**
   * Single Back Property is shared across multiple forms,
   * only one of them should run a validation process after user input applied.
   * Editor Mode means the user changed the value and the value wasn't delivered from upstream.
   */
  private val editorMode = AtomicBoolean(false)

  /**
   * There are several UIs for the same back property,
   * this method emulates the validation job for the current field when the user switches to another form.
   */
  private fun runJobFor3rdPartyValidation() {
    scope.launch {
      resetValidationJob {
        if (backProperty.get() == null) delay(1.minutes)
      }
    }
  }

  private suspend fun resetValidationJob(block: suspend CoroutineScope.() -> Unit) {
    validationJob?.cancelAndJoin()

    validationJob = scope.async(Dispatchers.EDT) {
      isEnabled = false
      isValidationActiveProperty.set(true)
      block()
    }.apply {
      invokeOnCompletion {
        isEnabled = true
        isValidationActiveProperty.set(false)
      }
    }
  }

  private val validationAction = object : DumbAwareAction(AllIcons.Gutter.SuggestedRefactoringBulb) {
    fun doValidate() {
      if (!editorMode.load()) return

      scope.launch {
        resetValidationJob {
          val exec = withContext(Dispatchers.IO) {
            val path = fileSystem.parsePath(text).getOr { error ->
              //TODO HANDLE PATH ERRORS
              return@withContext null
            }
            pathValidator.invoke(path)
          }
          backProperty.set(exec)
        }
      }
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
    backProperty.afterChange(scope.asDisposable()) { validatedPath ->
      if (validatedPath == null) {
        isValidationActiveProperty.set(true)
        if (!editorMode.load()) runJobFor3rdPartyValidation()
        return@afterChange
      }

      if (validatedPath.pathHolder != null) {
        text = validatedPath.pathHolder.toString()
      }
      validationJob?.cancel()
    }

    isValidationActiveProperty.afterChange(scope.asDisposable()) { isValidationActive ->
      with(textField as ExtendableTextComponent) {
        extensions.forEach { removeExtension(it) }

        if (isValidationActive) {
          if (validationJob?.isActive == true) {
            addExtension(ValidationInProgressExtension)
          }
          else {
            addExtension(validationWaitExtension)
          }
        }
        else {
          editorMode.store(false)
          if (browseFolderActionLister != null) {
            setButtonVisible(true)
          }
          backProperty.get()?.validationResult?.let { validationResult ->
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
    runJobFor3rdPartyValidation() // initial validation is processed by the common v2 model

    scope.launch {
      textInputFlow
        .debounce(50) // setText method is a combination of two calls - remove + insert, should count them as 1
        .map {
          if (it == null) return@map null

          if (!editorMode.load() && backProperty.get()?.pathHolder?.toString() != it) {
            editorMode.store(true)
            backProperty.set(null)
          }

          validationWaitIcon.reset()
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
  installAction: ActionLink? = null,
  validatedPathField: ValidatedPathField<T, P, V>,
): Row {
  val selectExecutableLink = ActionLink(message("sdk.create.custom.select.executable.link")) {
    validatedPathField.button.doClick()
  }
  val (firstFix, secondFix) = if (installAction == null || fileSystem.isReadOnly) {
    Pair(selectExecutableLink, null)
  }
  else {
    Pair(installAction, selectExecutableLink)
  }

  return row("") {
    validationTooltip(missingExecutableText,
                      firstFix,
                      secondFix,
                      validationType = ValidationType.WARNING,
                      inline = true)
      .align(Align.FILL)
      .component
  }
}

fun <P : PathHolder> Panel.validatableVenvField(
  propertyGraph: PropertyGraph,
  fileSystem: FileSystem<P>,
  backProperty: ObservableMutableProperty<ValidatedPath.Folder<P>?>,
  validationRequestor: DialogValidationRequestor,
  labelText: @Nls String,
  missingExecutableText: @Nls String?,
  installAction: ActionLink? = null,
  selectedPathValidator: suspend (P) -> ValidatedPath.Folder<P>,
): ValidatedPathField<Unit, P, ValidatedPath.Folder<P>> {
  return validatablePathField(
    propertyGraph = propertyGraph,
    fileSystem = fileSystem,
    backProperty = backProperty,
    validationRequestor = validationRequestor,
    labelText = labelText,
    missingExecutableText = missingExecutableText,
    installAction = installAction,
    isFileSelectionMode = false,
    selectedPathValidator = selectedPathValidator,
  )
}

fun <P : PathHolder> Panel.validatableExecutableField(
  propertyGraph: PropertyGraph,
  fileSystem: FileSystem<P>,
  backProperty: ObservableMutableProperty<ValidatedPath.Executable<P>?>,
  validationRequestor: DialogValidationRequestor,
  labelText: @Nls String,
  missingExecutableText: @Nls String?,
  installAction: ActionLink? = null,
  selectedPathValidator: suspend (P) -> ValidatedPath.Executable<P>,
): ValidatedPathField<Version, P, ValidatedPath.Executable<P>> {
  return validatablePathField(
    propertyGraph = propertyGraph,
    fileSystem = fileSystem,
    backProperty = backProperty,
    validationRequestor = validationRequestor,
    labelText = labelText,
    missingExecutableText = missingExecutableText,
    installAction = installAction,
    isFileSelectionMode = true,
    selectedPathValidator = selectedPathValidator,
  )
}

private fun <T, P : PathHolder, V : ValidatedPath<T, P>> Panel.validatablePathField(
  propertyGraph: PropertyGraph,
  fileSystem: FileSystem<P>,
  backProperty: ObservableMutableProperty<V?>,
  validationRequestor: DialogValidationRequestor,
  labelText: @Nls String,
  missingExecutableText: @Nls String?,
  installAction: ActionLink? = null,
  isFileSelectionMode: Boolean,
  selectedPathValidator: suspend (P) -> V,
): ValidatedPathField<T, P, V> {
  val isValidationActiveProperty = propertyGraph.property(false)

  val validatedPathField = ValidatedPathField(
    fileSystem = fileSystem,
    backProperty = backProperty,
    browseFolderDialogTitle = labelText,
    isFileSelectionMode = isFileSelectionMode,
    isValidationActiveProperty = isValidationActiveProperty,
    pathValidator = selectedPathValidator,
  )

  missingExecutableText?.let {
    installToolRow(
      fileSystem = fileSystem,
      missingExecutableText = missingExecutableText,
      installAction = installAction,
      validatedPathField = validatedPathField
    ).visibleIf(backProperty.isNull().and(isValidationActiveProperty.not()))
  }

  row(labelText) {
    cell(validatedPathField)
      .align(AlignX.FILL)
      .validationRequestor(validationRequestor
                             and WHEN_PROPERTY_CHANGED(isValidationActiveProperty)
                             and WHEN_PROPERTY_CHANGED(backProperty)
      )
      .validationOnInput { component ->
        if (!component.isVisible) return@validationOnInput null

        val pyErrorMessage = backProperty.get()?.validationResult?.errorOrNull?.message

        when {
          pyErrorMessage != null -> {
            ValidationInfo(pyErrorMessage)
          }
          isValidationActiveProperty.get() -> {
            ValidationInfo(message("python.add.sdk.wait.for.validation"))
          }
          else -> null
        }
      }
  }

  return validatedPathField
}