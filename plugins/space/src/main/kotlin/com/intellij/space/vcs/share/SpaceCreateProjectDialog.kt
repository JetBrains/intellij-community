// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.share

import circlet.client.api.PR_Project
import circlet.client.api.ProjectKey
import circlet.client.api.Projects
import circlet.client.pr
import circlet.platform.client.resolve
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.layout.*
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import libraries.coroutines.extra.LifetimeSource
import libraries.coroutines.extra.launch
import libraries.coroutines.extra.usingSource
import runtime.RpcException
import runtime.Ui
import runtime.message
import java.util.concurrent.CancellationException
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

internal class SpaceCreateProjectDialog(parent: JComponent) : DialogWrapper(parent, false) {
  private val lifetime: LifetimeSource = LifetimeSource()

  internal var result: PR_Project? = null

  // ui
  private val projectNameField: JBTextField = JBTextField()
  private val projectKeyField: JBTextField = JBTextField().apply {
    (document as AbstractDocument).documentFilter = ProjectKeyFilter()
  }
  private val privateCheckbox: JCheckBox = JCheckBox()
  private val projectDescriptionField: JBTextArea = JBTextArea()

  private val asyncProcessIcon = AsyncProcessIcon("Creating project...").apply {
    isVisible = false
    alignmentX = JComponent.LEFT_ALIGNMENT
  }

  init {
    title = SpaceBundle.message("create.project.dialog.title")
    setOKButtonText(SpaceBundle.message("create.project.dialog.ok.button"))
    init()
    Disposer.register(disposable, Disposable { lifetime.terminate() })
  }

  override fun doOKAction() {
    if (!okAction.isEnabled) return

    SpaceStatsCounterCollector.CREATE_NEW_PROJECT.log()
    launch(lifetime, Ui) {
      okAction.isEnabled = false
      asyncProcessIcon.isVisible = true
      lifetime.usingSource {
        val ws = SpaceWorkspaceComponent.getInstance().workspace.value ?: return@launch
        val client = ws.client
        val projectService: Projects = client.pr
        try {
          result = projectService.createProject(
            ProjectKey(projectKeyField.text),
            projectNameField.text,
            projectDescriptionField.text,
            privateCheckbox.isSelected
          ).resolve()
          close(OK_EXIT_CODE)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: RpcException) {
          setErrorText(e.failure.message()) // NON-NLS
        }
        catch (e: Exception) {
          setErrorText(SpaceBundle.message("create.project.dialog.error.unable.to.create.text", e.message ?: e.javaClass.simpleName))
        }
      }

      okAction.isEnabled = true
      asyncProcessIcon.isVisible = false
    }
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row(SpaceBundle.message("create.project.dialog.name.label")) {
        projectNameField()
      }
      row(SpaceBundle.message("create.project.dialog.key.label")) {
        projectKeyField().comment(
          HtmlBuilder()
            .append(SpaceBundle.message("create.project.dialog.key.comment")).br()
            .append(SpaceBundle.message("create.project.dialog.key.comment.cant.be.changed"))
            .toString(),
          maxLineLength = SpaceBundle.message("create.project.dialog.key.comment").length
        )
      }
      row(SpaceBundle.message("create.project.dialog.private.label")) {
        privateCheckbox().comment(SpaceBundle.message("create.project.dialog.private.comment"))
      }
      row(SpaceBundle.message("create.project.dialog.description.label")) {
        scrollPane(projectDescriptionField)
      }
    }
  }

  override fun createSouthPanel(): JComponent {
    val buttons = super.createSouthPanel()
    return JPanel(HorizontalLayout(8, SwingConstants.BOTTOM)).apply {
      asyncProcessIcon.border = buttons.border
      add(asyncProcessIcon, HorizontalLayout.RIGHT)
      add(buttons, HorizontalLayout.RIGHT)
    }
  }

  override fun doValidateAll(): MutableList<ValidationInfo> {
    val list = mutableListOf<ValidationInfo>()
    projectNameField.text.let {
      if (it.length < 2 || it.length > 100) {
        list.add(ValidationInfo(SpaceBundle.message("create.project.dialog.validation.info.name"), projectNameField))
      }
    }

    return list
  }

  override fun getDimensionServiceKey(): String = "com.intellij.space.vcs.share.CircletCreateProjectDialog"

  private class ProjectKeyFilter : DocumentFilter() {
    override fun replace(fb: FilterBypass?, offset: Int, length: Int, text: String?, attrs: AttributeSet?) {
      filterString(text)?.let {
        super.replace(fb, offset, length, it, attrs)
      }
    }

    override fun insertString(fb: FilterBypass?, offset: Int, string: String?, attr: AttributeSet?) {
      filterString(string)?.let {
        super.insertString(fb, offset, it, attr)
      }
    }

    private fun filterString(text: String?): String? {
      text ?: return null
      val all = CharRange('A', 'Z').plus('-').plus(CharRange('0', '9')).toSet()
      return text.toUpperCase().filter { it in all }
    }
  }
}
