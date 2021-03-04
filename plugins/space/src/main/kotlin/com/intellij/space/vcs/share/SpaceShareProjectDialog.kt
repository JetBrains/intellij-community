// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.share

import circlet.client.api.*
import circlet.client.repoService
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.promo.promoPanel
import com.intellij.space.settings.SpaceLoginState
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.ui.LoginComponents.buildConnectingPanel
import com.intellij.space.ui.LoginComponents.loginPanel
import com.intellij.space.utils.SpaceUrls
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.layout.*
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.LifetimeSource
import libraries.coroutines.extra.launch
import libraries.coroutines.extra.usingSource
import runtime.RpcException
import runtime.Ui
import runtime.message
import java.util.concurrent.CancellationException
import javax.swing.*

class SpaceShareProjectDialog(project: Project) : DialogWrapper(project, true) {
  private val contentWrapper: Wrapper = Wrapper().apply {
    putClientProperty(IS_VISUAL_PADDING_COMPENSATED_ON_COMPONENT_LEVEL_KEY, false)
  }

  private val lifetime: LifetimeSource = LifetimeSource()

  internal var result: Result? = null

  // ui
  private val projectComboBoxModel: CollectionComboBoxModel<PR_Project> = CollectionComboBoxModel()
  private val projectComboBox: ComboBox<PR_Project> = ComboBox(projectComboBoxModel).apply {
    renderer = SpaceProjectListComboRenderer()
  }
  private val createNewProjectButton: JButton = JButton(SpaceBundle.message("share.project.dialog.new.button.text")).apply {
    addActionListener {
      SpaceStatsCounterCollector.START_CREATING_NEW_PROJECT.log()
      val createProjectDialog = SpaceCreateProjectDialog(this@SpaceShareProjectDialog.contentPanel)
      if (createProjectDialog.showAndGet()) {
        createProjectDialog.result?.let {
          projectComboBoxModel.add(it)
          projectComboBox.selectedItem = it
        }
      }
    }
  }

  private val repoNameField: JBTextField = JBTextField().apply {
    text = project.name
  }

  private val asyncProcessIcon = AsyncProcessIcon("Creating project...").apply {
    isVisible = false
    alignmentX = JComponent.LEFT_ALIGNMENT
  }

  private val loadingProjectsProgress = AsyncProcessIcon("Loading projects...").apply {
    isVisible = true
  }

  private val repoDescription: JBTextField = JBTextField()

  init {
    title = SpaceBundle.message("share.project.dialog.title")
    setOKButtonText(SpaceBundle.message("share.project.dialog.ok.button.text"))
    init()
    Disposer.register(disposable, Disposable { lifetime.terminate() })

    SpaceWorkspaceComponent.getInstance().loginState.forEach(lifetime) { state ->
      okAction.isEnabled = false
      val view = when (state) {
        is SpaceLoginState.Disconnected -> buildShareLoginPanel(state) { serverName ->
          SpaceWorkspaceComponent.getInstance().signInManually(serverName, lifetime, contentWrapper)
        }
        is SpaceLoginState.Connected -> {
          okAction.isEnabled = true
          buildShareProjectPanel(lifetime)
        }
        is SpaceLoginState.Connecting -> buildConnectingPanel(state, SpaceStatsCounterCollector.LoginPlace.SHARE) {
          state.cancel()
        }
      }.apply {
        border = JBUI.Borders.empty(8, 12)
      }
      contentWrapper.setContent(view)
      contentWrapper.validate()
      contentWrapper.repaint()
    }
  }

  private fun buildShareLoginPanel(state: SpaceLoginState.Disconnected, loginAction: (String) -> Unit): JComponent {
    return panel {
      loginPanel(state, SpaceStatsCounterCollector.LoginPlace.SHARE, isLoginActionDefault = true) {
        loginAction(it)
      }
      promoPanel(SpaceStatsCounterCollector.ExplorePlace.SHARE)
    }
  }

  override fun doOKAction() {
    if (!okAction.isEnabled) return
    SpaceStatsCounterCollector.SHARE_PROJECT.log()
    launch(lifetime, Ui) {
      okAction.isEnabled = false
      asyncProcessIcon.isVisible = true
      lifetime.usingSource {
        val ws = SpaceWorkspaceComponent.getInstance().workspace.value ?: return@launch
        val client = ws.client
        val repoService: RepositoryService = client.repoService
        val prKey = projectComboBoxModel.selected!!.key
        try {
          val repository = repoService.createNewRepository(prKey.identifier,
                                                           repoNameField.text,
                                                           repoDescription.text.orEmpty(),
                                                           initialize = false) // always create empty repo
          val details = repoService.repositoryDetails(prKey, repository.name)

          val url = SpaceUrls.repo(prKey, repository.name)

          result = Result.ProjectCreated(repository, details, url)
          close(OK_EXIT_CODE)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: RpcException) {
          setErrorText(e.failure.message()) // NON-NLS
        }
        catch (e: Exception) {
          setErrorText(SpaceBundle.message("share.project.dialog.error.unable.to.create.repository", e.message ?: e.javaClass.simpleName))
        }
      }

      okAction.isEnabled = true
      asyncProcessIcon.isVisible = false
    }
  }

  override fun createCenterPanel(): JComponent = contentWrapper

  override fun getPreferredFocusedComponent(): JComponent = contentWrapper

  private fun buildShareProjectPanel(lifetime: Lifetime): DialogPanel {
    val shareProjectVM = SpaceShareProjectVM(lifetime)

    shareProjectVM.projectsListState.forEach(lifetime) { projectListState ->
      when (projectListState) {
        is SpaceShareProjectVM.ProjectListState.Loading -> {
          loadingProjectsProgress.isVisible = true
        }
        is SpaceShareProjectVM.ProjectListState.Error -> {
          loadingProjectsProgress.isVisible = false
          setErrorText(projectListState.error)
        }
        is SpaceShareProjectVM.ProjectListState.Projects -> {
          loadingProjectsProgress.isVisible = false
          projectComboBoxModel.removeAll()
          projectComboBoxModel.add(projectListState.projects)
          if (projectListState.projects.isNotEmpty()) {
            projectComboBox.selectedIndex = 0
          }
        }
      }
    }

    return panel {
      row {
        cell(isFullWidth = true) {
          JLabel(SpaceBundle.message("share.project.dialog.create.repository.label"))()
          projectComboBox(pushX, growX)
          loadingProjectsProgress()
          createNewProjectButton()
        }
      }
      row(SpaceBundle.message("share.project.dialog.repository.name.label")) {
        repoNameField(pushX, growX)
      }
      row(SpaceBundle.message("share.project.dialog.repository.description.label")) {
        repoDescription(pushX, growX)
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
    if (projectComboBoxModel.selectedItem == null) {
      list.add(ValidationInfo(SpaceBundle.message("share.project.dialog.validation.text.project.should.be.specified"), projectComboBox))
    }
    val nameError = repositoryNameValid(repoNameField.text).second // NON-NLS
    if (nameError != null) {
      list.addIfNotNull(ValidationInfo(nameError, repoNameField))
    }
    return list
  }

  override fun getDimensionServiceKey(): String = "com.intellij.space.vcs.share.CircletShareProjectDialog"

  open class Result() {
    data class ProjectCreated(val project: PR_RepositoryInfo, val repo: RepoDetails,  val url: String) : Result()
    object Canceled : Result()
    object NotCreated : Result()
  }
}

