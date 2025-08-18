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
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private class ValidationSuccessExtension<T>(val validationInfo: T) : ExtendableTextComponent.Extension {
  override fun getIcon(hovered: Boolean): javax.swing.Icon = AllIcons.General.GreenCheckmark
  override fun getTooltip(): @NlsContexts.Tooltip String? {
    val tooltip = when (validationInfo) {
      is Unit -> null
      else -> validationInfo.toString().takeIf { it.isNotEmpty() }
    }
    return tooltip
  }
}

private object ValidationErrorExtension : ExtendableTextComponent.Extension {
  override fun getIcon(hovered: Boolean): javax.swing.Icon = AllIcons.Status.FailedInProgress
}

private object ValidationInProgressExtension : ExtendableTextComponent.Extension {
  override fun getIcon(hovered: Boolean): javax.swing.Icon = AnimatedIcon.Default()
  override fun getTooltip(): @NlsContexts.Tooltip String {
    return message("python.add.sdk.wait.for.validation")
  }
}


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
  private val textInputFlow = MutableStateFlow("")
  private var validationJob: Deferred<Unit>? = null
  private val owner = AtomicBoolean(false)

  private val validationAction = object : DumbAwareAction(AllIcons.Gutter.SuggestedRefactoringBulb) {
    fun doValidate() {
      if (!owner.load()) return

      scope.launch {
        validationJob?.cancelAndJoin()

        isEnabled = false
        validationJob = scope.async(Dispatchers.EDT) {
          isValidationActiveProperty.set(true)

          val exec = withContext(Dispatchers.IO) {
            val path = fileSystem.parsePath(text).getOr { error ->
              //TODO HANDLE PATH ERRORS
              return@withContext null
            }

            pathValidator.invoke(path)
          }

          backProperty.set(exec)
        }

        validationJob?.invokeOnCompletion {
          isEnabled = true
          isValidationActiveProperty.set(false)
        }
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      with(textField as ExtendableTextComponent) {
        if (extensions.contains(runValidationExtension)) {
          doValidate()
        }
      }
    }
  }.apply {
    registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)), this@ValidatedPathField)
  }

  private val runValidationExtension: ExtendableTextComponent.Extension = ExtendableTextComponent.Extension.create(
    AllIcons.Gutter.SuggestedRefactoringBulb, message("python.add.sdk.wait.for.validation")
  ) {
    validationAction.doValidate()
  }

  private val fieldAccessor = object : TextComponentAccessor<JTextField> {
    override fun getText(component: JTextField): @NlsSafe String {
      return component.text
    }

    override fun setText(component: JTextField, text: @NlsSafe String) {
      component.text = text
      owner.store(true)
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


  fun initialize(scope: CoroutineScope) {
    this.scope = scope

    scope.launch {
      textInputFlow
        .debounce(100)
        .map {
          if (backProperty.get()?.pathHolder?.toString() != it) {
            owner.store(true)
            backProperty.set(null)
          }
          it
        }
        .debounce(2000)
        .collectLatest {
          validationAction.doValidate()
        }
    }

    backProperty.afterChange(scope.asDisposable()) { validatedPath ->
      if (validatedPath == null) {
        isValidationActiveProperty.set(true)
        if (!owner.load()) isEnabled = false
        return@afterChange
      }

      val validatedPathText = validatedPath.pathHolder?.toString() ?: ""
      text = validatedPathText
      isEnabled = true
      isValidationActiveProperty.set(false)
    }

    isValidationActiveProperty.afterChange(scope.asDisposable()) { isValidationActive ->
      with(textField as ExtendableTextComponent) {
        extensions.forEach { removeExtension(it) }

        if (isValidationActive) {
          if (validationJob?.isActive == true) {
            addExtension(ValidationInProgressExtension)
          }
          else {
            addExtension(runValidationExtension)
          }
        }
        else {
          owner.store(false)
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