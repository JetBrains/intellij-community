package circlet.vcs.share

import circlet.client.api.*
import circlet.client.repoService
import circlet.components.circletWorkspace
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
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

class CircletShareProjectDialog(project: Project) : DialogWrapper(project, true) {
    private val lifetime: LifetimeSource = LifetimeSource()

    internal var result: Result? = null

    // vm
    private val shareProjectVM: CircletShareProjectVM = CircletShareProjectVM(lifetime)

    // ui
    private val projectComboBoxModel: CollectionComboBoxModel<PR_Project> = CollectionComboBoxModel()
    private val projectComboBox: ComboBox<PR_Project> = ComboBox(projectComboBoxModel).apply {
        renderer = CircletProjectListComboRenderer()
    }
    private val createNewProjectButton: JButton = JButton("New...").apply {
        addActionListener {
            val createProjectDialog = CircletCreateProjectDialog(this@CircletShareProjectDialog.contentPanel)
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
        title = "Share Project on Space"
        setOKButtonText("Share")
        init()
        Disposer.register(disposable, Disposable { lifetime.terminate() })

        shareProjectVM.projectsListState.forEach(lifetime) { projectListState ->
            when (projectListState) {
                is CircletShareProjectVM.ProjectListState.Loading -> {
                    loadingProjectsProgress.isVisible = true
                }
                is CircletShareProjectVM.ProjectListState.Error -> {
                    loadingProjectsProgress.isVisible = false
                    setErrorText(projectListState.error)
                }
                is CircletShareProjectVM.ProjectListState.Projects -> {
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
                val ws = circletWorkspace.workspace.value ?: return@launch
                val client = ws.client
                val repoService: RepositoryService = client.repoService
                val prKey = projectComboBoxModel.selected!!.key
                try {
                    val repository = repoService.createNewRepository(prKey,
                                                                  repoNameField.text,
                                                                  repoDescription.text.orEmpty(),
                                                                  initialize = false) // always create empty repo
                    val details = repoService.repositoryDetails(prKey, repository.name)

                    val url = Navigator.p.project(prKey).repo(repository.name).absoluteHref(client.server)

                    result = Result(repository, details, url)
                    close(OK_EXIT_CODE)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RpcException) {
                    setErrorText(e.failure.message())
                } catch (e: Exception) {
                    setErrorText("Unable to create repository: ${e.message}")
                }
            }

            okAction.isEnabled = true
            asyncProcessIcon.isVisible = false
        }
    }

    override fun createCenterPanel(): JComponent = panel {
        row {
            cell(isFullWidth = true) {
                JLabel("Create Repository in Project:")()
                projectComboBox(pushX, growX)
                loadingProjectsProgress()
                createNewProjectButton()
            }
        }
        row("Repository name:") {
            repoNameField(pushX, growX)
        }
        row("Repository description:") {
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
            list.add(ValidationInfo("Project should be specified", projectComboBox))
        }
        val nameError = repositoryNameValid(repoNameField.text).second
        if (nameError != null) {
            list.addIfNotNull(ValidationInfo(nameError, repoNameField))
        }
        return list
    }

    override fun getDimensionServiceKey(): String = "circlet.vcs.share.CircletShareProjectDialog"

    data class Result(
        val project: PR_RepositoryInfo,
        val repo: RepoDetails,
        val url: String
    )
}

