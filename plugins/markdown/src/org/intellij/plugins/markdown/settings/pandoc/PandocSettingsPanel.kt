// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings.pandoc

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.intellij.plugins.markdown.MarkdownBundle
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

internal class PandocSettingsPanel(private val project: Project): JPanel(GridBagLayout()) {
  private val executablePathSelector = TextFieldWithBrowseButton()
  private val testButton = JButton(MarkdownBundle.message("markdown.settings.pandoc.executable.test"))
  private val infoPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
  private val imagesPathSelector = TextFieldWithBrowseButton()

  private val settings
    get() = PandocSettings.getInstance(project)

  val executablePath
    get() = executablePathSelector.text.takeIf { it.isNotEmpty() }

  val imagesPath
    get() = imagesPathSelector.text.takeIf { it.isNotEmpty() }

  init {
    val gb = GridBag().apply {
      defaultAnchor = GridBagConstraints.WEST
      defaultFill = GridBagConstraints.HORIZONTAL
      nextLine().next()
    }
    add(
      JBLabel(MarkdownBundle.message("markdown.settings.pandoc.executable.label")),
      gb.insets(JBUI.insetsRight(UIUtil.DEFAULT_HGAP))
    )
    add(executablePathSelector, gb.next().fillCellHorizontally().weightx(1.0).insets(0, 0, 1, 0))
    add(testButton, gb.next())
    gb.nextLine().next()
    add(infoPanel, gb.next().insets(4, 4, 0, 0))
    gb.nextLine().next()
    add(
      JBLabel(MarkdownBundle.message("markdown.settings.pandoc.resource.path.label")),
      gb.insets(JBUI.insetsRight(UIUtil.DEFAULT_HGAP))
    )
    add(imagesPathSelector, gb.next().coverLine().insets(0, 0, 1, 0))
    testButton.addActionListener {
      infoPanel.removeAll()
      val path = executablePath ?: PandocExecutableDetector.detect()
      val labelText = when (val detectedVersion = PandocExecutableDetector.obtainPandocVersion(project, path)) {
        null -> MarkdownBundle.message("markdown.settings.pandoc.executable.error.msg", path)
        else -> MarkdownBundle.message("markdown.settings.pandoc.executable.success.msg", detectedVersion)
      }
      infoPanel.add(JBLabel(labelText).apply { border = IdeBorderFactory.createEmptyBorder(JBUI.insetsBottom(4)) })
    }
    setupFileChooser(
      browser = executablePathSelector,
      descriptor = FileChooserDescriptor(true, false, true, true, false, false),
      defaultValue = { settings.pathToPandoc }
    )
    setupFileChooser(
      browser = imagesPathSelector,
      descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor(),
      defaultValue = { settings.pathToImages }
    )
    reset()
  }

  private fun setupFileChooser(browser: TextFieldWithBrowseButton, descriptor: FileChooserDescriptor, defaultValue: () -> String?) {
    defaultValue()?.takeIf { it.isNotEmpty() }?.let {
      imagesPathSelector.text = it
    }
    browser.addActionListener {
      val lastFile = browser.text.takeIf { it.isNotEmpty() }?.let { VfsUtil.findFileByIoFile(File(it), false) }
      val files = FileChooser.chooseFiles(descriptor, project, lastFile)
      if (files.size == 1) {
        browser.text = files.first().presentableUrl
      }
    }
    FileChooserFactory.getInstance().installFileCompletion(
      browser.textField,
      descriptor,
      true,
      browser
    )
  }

  fun apply() {
    settings.pathToPandoc = executablePath
    settings.pathToImages = imagesPath
  }

  fun isModified(): Boolean {
    return executablePath != settings.pathToPandoc || imagesPath != settings.pathToImages
  }

  private fun updateExecutablePathSelectorEmptyText() {
    val detectedPath = PandocExecutableDetector.detect()
    if (detectedPath.isNotEmpty()) {
      (executablePathSelector.textField as JBTextField).emptyText.text = MarkdownBundle.message(
        "markdown.settings.pandoc.executable.auto",
        detectedPath
      )
    } else {
      (executablePathSelector.textField as JBTextField).emptyText.text = MarkdownBundle.message(
        "markdown.settings.pandoc.executable.default.error.msg"
      )
    }
  }

  fun reset() {
    when (val path = settings.pathToPandoc) {
      null -> {
        executablePathSelector.text = ""
        updateExecutablePathSelectorEmptyText()
      }
      else -> {
        executablePathSelector.text = path
      }
    }
    imagesPathSelector.text = settings.pathToImages ?: ""
  }
}
