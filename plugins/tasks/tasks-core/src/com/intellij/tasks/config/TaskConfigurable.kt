// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.config

import com.intellij.icons.AllIcons
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.tasks.CommitPlaceholderProvider
import com.intellij.tasks.TaskBundle
import com.intellij.tasks.TaskManager
import com.intellij.tasks.impl.BaseRepositoryImpl
import com.intellij.tasks.impl.TaskManagerImpl
import com.intellij.ui.EditorSettingsProvider
import com.intellij.ui.EditorTextField
import com.intellij.ui.ExtendableEditorSupport
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.dsl.builder.*
import kotlin.reflect.KMutableProperty0

class TaskConfigurable(private val project: Project) : BoundSearchableConfigurable(
  TaskBundle.message("configurable.TaskConfigurable.display.name"),
  "reference.settings.project.tasks", "tasks"), SearchableConfigurable.Parent, NoScroll {

  private val changelistNameFormat: EditorTextField
  private val branchNameFormat: EditorTextField

  private val lazyConfigurables: Array<Configurable> by lazy { arrayOf(TaskRepositoriesConfigurable(project)) }

  init {
    val fileType = FileTypeManager.getInstance().findFileTypeByName("VTL") ?: PlainTextFileType.INSTANCE
    val defaultProject = ProjectManager.getInstance().getDefaultProject()
    branchNameFormat = EditorTextField(defaultProject, fileType)
    setupAddAction(branchNameFormat)
    changelistNameFormat = EditorTextField(defaultProject, fileType)
    setupAddAction(changelistNameFormat)

  }

  override fun createPanel(): DialogPanel {
    val settings = TaskSettings.getInstance()
    val config = getConfig()

    return panel {
      row(TaskBundle.message("settings.changelist.name.format")) {
        cell(changelistNameFormat)
          .bindText(config::changelistNameFormat)
          .align(AlignX.FILL)
      }
      row(TaskBundle.message("settings.feature.branch.name.format")) {
        cell(branchNameFormat)
          .bindText(config::branchNameFormat)
          .align(AlignX.FILL)
      }
      row("") {
        checkBox(TaskBundle.message("settings.lowercased"))
          .bindSelected(settings::LOWER_CASE_BRANCH)
          .resizableColumn()

        textField()
          .bindText(settings::REPLACE_SPACES)
          .columns(1)
          .label(TaskBundle.message("settings.replace.spaces.with"))
      }
      row(TaskBundle.message("settings.task.history.length")) {
        intTextField()
          .columns(COLUMNS_TINY)
          .bindIntText(config::taskHistoryLength)
      }
      row(TaskBundle.message("settings.connection.timeout")) {
        intTextField()
          .columns(COLUMNS_TINY)
          .bindIntText(settings::CONNECTION_TIMEOUT)
          .gap(RightGap.SMALL)

        @Suppress("DialogTitleCapitalization")
        label(TaskBundle.message("settings.milliseconds"))
      }
      row {
        checkBox(TaskBundle.message("settings.always.display.task.combo.in.toolbar"))
          .bindSelected(settings::ALWAYS_DISPLAY_COMBO)
      }
      row {
        checkBox(TaskBundle.message("settings.save.context.on.commit"))
          .bindSelected(config::saveContextOnCommit)
      }

      group(TaskBundle.message("settings.issue.cache")) {
        row {
          val updateCheckBox = checkBox(TaskBundle.message("settings.enable.cache"))
            .bindSelected(config::updateEnabled)

          intTextField()
            .bindIntText(config::updateIssuesCount)
            .enabledIf(updateCheckBox.selected)
            .label(TaskBundle.message("settings.Update"))
            .columns(4)
            .gap(RightGap.SMALL)

          @Suppress("DialogTitleCapitalization")
          intTextField()
            .bindIntText(config::updateInterval)
            .enabledIf(updateCheckBox.selected)
            .label(TaskBundle.message("settings.issues.every"))
            .columns(4)
            .gap(RightGap.SMALL)

          @Suppress("DialogTitleCapitalization")
          label(TaskBundle.message("settings.minutes"))
            .enabledIf(updateCheckBox.selected)
        }
      }
    }
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    if (changelistNameFormat.getText().trim().isEmpty()) {
      throw ConfigurationException(TaskBundle.message("settings.change.list.name.format.should.not.be.empty"))
    }
    if (branchNameFormat.getText().trim().isEmpty()) {
      throw ConfigurationException(TaskBundle.message("settings.Branch.name.format.should.not.be.empty"))
    }

    val oldConnectionTimeout = TaskSettings.getInstance().CONNECTION_TIMEOUT
    val oldUpdateEnabled = getConfig().updateEnabled

    super.apply()

    if (project.isDefault()) {
      return
    }

    val manager = TaskManager.getManager(project)
    if (getConfig().updateEnabled && !oldUpdateEnabled) {
      manager.updateIssues(null)
    }

    if (TaskSettings.getInstance().CONNECTION_TIMEOUT != oldConnectionTimeout) {
      for (repository in manager.getAllRepositories()) {
        if (repository is BaseRepositoryImpl) {
          repository.reconfigureClient()
        }
      }
    }
  }

  override fun getConfigurables(): Array<out Configurable> {
    return lazyConfigurables
  }

  override fun hasOwnContent(): Boolean {
    return true
  }

  private fun getConfig(): TaskManagerImpl.Config {
    return (TaskManager.getManager(project) as TaskManagerImpl).getState()
  }

  private fun setupAddAction(field: EditorTextField) {
    field.addSettingsProvider(EditorSettingsProvider { editor: EditorEx ->
      val extension =
        ExtendableTextComponent.Extension
          .create(AllIcons.General.InlineAdd, AllIcons.General.InlineAddHover, TaskBundle.message("settings.add.placeholder"), Runnable {
            val placeholders = HashSet<String>()
            for (provider in CommitPlaceholderProvider.EXTENSION_POINT_NAME.extensionList) {
              placeholders.addAll(provider.getPlaceholders(null))
            }
            JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<String>(TaskBundle.message("settings.placeholders"),
                                                                                            placeholders.toList()) {
              override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*> {
                WriteCommandAction.runWriteCommandAction(project, Runnable {
                  editor.getDocument()
                    .insertString(editor.getCaretModel().offset, "\${$selectedValue}")
                })
                return FINAL_CHOICE
              }
            }).showInBestPositionFor(editor)
          })
      ExtendableEditorSupport.setupExtension(editor, field.getBackground(), extension)
    })
  }

  private fun Cell<EditorTextField>.bindText(prop: KMutableProperty0<String>): Cell<EditorTextField> {
    return bind(
      componentGet = { component -> component.getText() },
      componentSet = { component, value -> component.setText(value) },
      prop = prop.toMutableProperty()
    )
  }
}
