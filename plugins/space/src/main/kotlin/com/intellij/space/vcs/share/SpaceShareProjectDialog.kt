package com.intellij.space.vcs.share

import circlet.client.api.*
import circlet.client.repoService
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.space.components.space
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.layout.*
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import libraries.coroutines.extra.LifetimeSource
import libraries.coroutines.extra.launch
import libraries.coroutines.extra.usingSource
import runtime.RpcException
import runtime.Ui
import runtime.message
import java.util.concurrent.CancellationException
import javax.swing.*

class SpaceShareProjectDialog(project: Project) : DialogWrapper(project, true) {
  private val lifetime: LifetimeSource = LifetimeSource()

  internal var result: Result? = null

  // vm
  private val shareProjectVM: SpaceShareProjectVM = SpaceShareProjectVM(lifetime)

  // ui
  private val projectComboBoxModel: CollectionComboBoxModel<PR_Project> = CollectionComboBoxModel()
  private val projectComboBox: ComboBox<PR_Project> = ComboBox(projectComboBoxModel).apply {
    renderer = SpaceProjectListComboRenderer()
  }
  private val createNewProjectButton: JButton = JButton(SpaceBundle.message("share.project.dialog.new.button.text")).apply {
    addActionListener {
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
          projectComboBoxModel.addAll(0, projectListState.projects)
          if (projectListState.projects.isNotEmpty()) {
            projectComboBox.selectedIndex = 0
          }
        }
      }
    }
  }

  override fun doOKAction() {
    if (!okAction.isEnabled) return

    launch(lifetime, Ui) {
      okAction.isEnabled = false
      asyncProcessIcon.isVisible = true
      lifetime.usingSource {
        val ws = space.workspace.value ?: return@launch
        val client = ws.client
        val repoService: RepositoryService = client.repoService
        val prKey = projectComboBoxModel.selected!!.key
        try {
          val repository = repoService.createNewRepository(prKey.identifier,
                                                           repoNameField.text,
                                                           repoDescription.text.orEmpty(),
                                                           initialize = false) // always create empty repo
          val details = repoService.repositoryDetails(prKey, repository.name)

          val url = Navigator.p.project(prKey).repo(repository.name).absoluteHref(client.server)

          result = Result(repository, details, url)
          close(OK_EXIT_CODE)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: RpcException) {
          setErrorText(e.failure.message())
        }
        catch (e: Exception) {
          setErrorText(SpaceBundle.message("share.project.dialog.error.unable.to.create.repository", e.message ?: e.javaClass.simpleName))
        }
      }

      okAction.isEnabled = true
      asyncProcessIcon.isVisible = false
    }
  }

  override fun createCenterPanel(): JComponent = panel {
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

  override fun createSouthPanel(): JComponent {
    val buttons = super.createSouthPanel()
    return JPanel(HorizontalLayout(JBUI.scale(8), SwingConstants.BOTTOM)).apply {
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
    val nameError = repositoryNameValid(repoNameField.text).second
    if (nameError != null) {
      list.addIfNotNull(ValidationInfo(nameError, repoNameField))
    }
    return list
  }

  override fun getDimensionServiceKey(): String = "com.intellij.space.vcs.share.CircletShareProjectDialog"

  data class Result(
    val project: PR_RepositoryInfo,
    val repo: RepoDetails,
    val url: String
  )
}

