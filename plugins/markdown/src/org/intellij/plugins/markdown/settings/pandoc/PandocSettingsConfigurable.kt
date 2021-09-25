// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings.pandoc

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import com.intellij.util.ui.UIUtil
import org.intellij.plugins.markdown.MarkdownBundle
import java.io.File
import javax.swing.JComponent
import javax.swing.JLabel

class PandocSettingsConfigurable :
  BoundConfigurable(MarkdownBundle.message("markdown.settings.pandoc.name")),
  SearchableConfigurable {

  private lateinit var imagesFromFilePath: TextFieldWithBrowseButton
  private lateinit var pandocPath: TextFieldWithBrowseButton
  private lateinit var pandocVersionField: JLabel
  private lateinit var downloadLink: JComponent

  private val pandocDetector = PandocExecutableDetector()
  private val pandocAppSettings get() = PandocApplicationSettings.getInstance()

  override fun createPanel(): DialogPanel = panel {
    row {
      cell {
        label(MarkdownBundle.message("markdown.settings.pandoc.executable.label"))
        pandocPath = createPandocPathField()

        button(MarkdownBundle.message("markdown.settings.pandoc.executable.test")) { testButtonActionEvent() }
      }
    }
    row {
      cell {
        pandocVersionField = JLabel().apply {
          isOpaque = false
        }
        cell.component(pandocVersionField)
      }
    }
    row {
      cell {
        downloadLink = createDownloadAndInstallLink()
        cell.component(downloadLink)
      }
    }
    row {
      cell {
        label(MarkdownBundle.message("markdown.settings.pandoc.resource.path.label"))
      }
    }
    row {
      cell {
        imagesFromFilePath = createResourcesPathField()
      }
    }
  }

  override fun getId(): String = ID

  override fun apply() {
    pandocAppSettings.apply {
      state.myPathToPandoc = pandocPath.textField.text
      state.myPathToImages = imagesFromFilePath.textField.text
    }
  }

  override fun isModified(): Boolean {
    val pandocSavedPath = pandocAppSettings.state.myPathToPandoc
    val resSavedPath = pandocAppSettings.state.myPathToImages
    val isImagePathModified = if (resSavedPath != null) isModified(imagesFromFilePath.textField, resSavedPath) else false

    if (pandocSavedPath == null) {
      return notAutodetectedTextAvailable() && isImagePathModified
    }

    return isModified(pandocPath.textField, pandocSavedPath) || isImagePathModified
  }

  private fun notAutodetectedTextAvailable() =
    (pandocPath.textField as JBTextField).emptyText.text.isEmpty() || pandocPath.textField.text.isNotEmpty()

  override fun reset() {
    pandocPath.textField.text = pandocAppSettings.state.myPathToPandoc
    imagesFromFilePath.textField.text = pandocAppSettings.state.myPathToImages
  }

  private fun testButtonActionEvent() {
    val executable = pandocPath.textField.text.ifEmpty { pandocDetector.detect() }
    val isExecExist = File(executable).exists()
    val pandocVersion = getPandocVersion(isExecExist, executable)
    val foundVersion = pandocVersion != null
    val isCanceled = pandocDetector.isCanceled

    pandocVersionField.text = when {
      isCanceled -> MarkdownBundle.message("markdown.settings.pandoc.executable.cancel.msg")
      foundVersion && isExecExist -> MarkdownBundle.message("markdown.settings.pandoc.executable.success.msg", pandocVersion)
      foundVersion && !isExecExist -> MarkdownBundle.message("markdown.settings.pandoc.executable.default.success.msg", pandocVersion)
      !foundVersion && isExecExist -> MarkdownBundle.message("markdown.settings.pandoc.executable.error.msg", executable)
      else -> MarkdownBundle.message("markdown.settings.pandoc.executable.default.error.msg")
    }

    pandocVersionField.foreground = when {
      foundVersion || isCanceled -> UIUtil.getTextFieldForeground()
      else -> UIUtil.getErrorForeground()
    }

    downloadLink.isVisible = !foundVersion && !isCanceled
  }

  private fun getPandocVersion(isExecExist: Boolean, executable: String): String? {
    val project = getProject()

    return when {
      project == null -> null
      isExecExist -> pandocDetector.tryToGetPandocVersion(project, executable)
      else -> pandocDetector.tryToGetPandocVersion(project)
    }
  }

  private fun Cell.createPandocPathField(): TextFieldWithBrowseButton {
    return textFieldWithBrowseButton().applyToComponent {
      val savedPath = pandocAppSettings.state.myPathToPandoc
      if (savedPath == null) {
        (textField as JBTextField).emptyText.text = "${
          MarkdownBundle.message("markdown.settings.pandoc.executable.auto")
        } ${pandocDetector.detect()}"
      }
      else {
        textField.text = savedPath
      }
    }.component
  }

  private fun Cell.createDownloadAndInstallLink() = link(MarkdownBundle.message("markdown.settings.pandoc.executable.link.download")) {
    BrowserUtil.browse("https://pandoc.org/installing.html")
  }.applyToComponent {
    isVisible = false
  }.component

  private fun Cell.createResourcesPathField(): TextFieldWithBrowseButton = textFieldWithBrowseButton().applyToComponent {
    val savedPath = pandocAppSettings.state.myPathToImages
    if (savedPath == null) {
      val project = getProject()
      if (project != null && project.basePath !== null) {
        pandocAppSettings.state.myPathToImages = "${project.basePath!!}/import-resources"
        textField.text = pandocAppSettings.state.myPathToImages
      }
    }else {
      textField.text = savedPath
    }
  }.component

  private fun getProject(): Project? {
    val projectList = ProjectManager.getInstance().openProjects
    return if (projectList.isNotEmpty()) projectList.first() else null
  }

  companion object {
    const val ID = "Settings.Pandoc"
  }
}
