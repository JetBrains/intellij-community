// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.core.forgejo

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.tasks.TaskBundle
import com.intellij.tasks.config.BaseRepositoryEditor
import com.intellij.tasks.core.forgejo.model.ForgejoProject
import com.intellij.tasks.impl.TaskUiUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.Consumer
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.SwingConstants

class ForgejoRepositoryEditor(
  project: Project,
  repository: ForgejoRepository,
  changeListener: Consumer<in ForgejoRepository>,
) : BaseRepositoryEditor<ForgejoRepository>(project, repository, changeListener) {

  private lateinit var projectLabel: JBLabel
  private lateinit var projectComboBox: ComboBox<ForgejoProject?>

  init {
    myPasswordLabel.text = TaskBundle.message("label.token")

    myUsernameLabel.isVisible = false
    myUserNameText.isVisible = false

    myTestButton.isEnabled = myRepository.isConfigured

    installListener(projectComboBox)

    UIUtil.invokeLaterIfNeeded { initialize() }
  }

  private fun initialize() {
    val currentProject = myRepository.currentProject
    if (currentProject != null && myRepository.isConfigured) {
      FetchProjectsTask().queue()
    }
  }

  override fun createCustomPanel(): JComponent {
    projectLabel = JBLabel(TaskBundle.message("label.project"), SwingConstants.RIGHT)
    projectComboBox = ComboBox<ForgejoProject?>(300)

    projectComboBox.renderer = listCellRenderer {
      val project = value
      when (project) {
        null -> text(TaskBundle.message("label.set.server.url.token.first"))
        is ForgejoProject -> {
          text(project.toString()) {
            if (project.id == -1) {
              foreground = greyForeground
            }
          }
        }
      }
    }
    projectLabel.setLabelFor(projectComboBox)
    return FormBuilder().addLabeledComponent(projectLabel, projectComboBox).panel
  }

  override fun afterTestConnection(connectionSuccessful: Boolean) {
    if (connectionSuccessful) {
      FetchProjectsTask().queue()
    }
  }

  override fun apply() {
    super.apply()
    myRepository.currentProject = projectComboBox.selectedItem as? ForgejoProject
    myTestButton.isEnabled = myRepository.isConfigured
  }

  private inner class FetchProjectsTask : TaskUiUtil.ComboBoxUpdater<ForgejoProject>(
    this@ForgejoRepositoryEditor.myProject,
    TaskBundle.message("progress.title.downloading.forgejo.repositories"),
    projectComboBox
  ) {
    override fun getExtraItem(): ForgejoProject = ForgejoRepository.UNSPECIFIED_PROJECT

    override fun getSelectedItem(): ForgejoProject? = myRepository.currentProject

    override fun fetch(indicator: ProgressIndicator): List<ForgejoProject> {
      return myRepository.fetchRepos()
    }
  }
}