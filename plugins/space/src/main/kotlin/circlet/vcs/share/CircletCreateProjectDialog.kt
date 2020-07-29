package circlet.vcs.share

import circlet.client.api.PR_Project
import circlet.client.api.ProjectKey
import circlet.client.api.Projects
import circlet.client.pr
import circlet.components.circletWorkspace
import circlet.platform.client.resolve
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
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

internal class CircletCreateProjectDialog(parent: JComponent) : DialogWrapper(parent, false) {
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
        title = "Create New Project on Space"
        setOKButtonText("Create")
        init()
        Disposer.register(disposable, Disposable { lifetime.terminate() })
    }

    override fun doOKAction() {
        if (!okAction.isEnabled) return

        launch(lifetime, Ui) {
            okAction.isEnabled = false
            asyncProcessIcon.isVisible = true
            lifetime.usingSource {
                val ws = circletWorkspace.workspace.value ?: return@launch
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RpcException) {
                    setErrorText(e.failure.message())
                } catch (e: Exception) {
                    setErrorText("Unable to create project: ${e.message}")
                }
            }

            okAction.isEnabled = true
            asyncProcessIcon.isVisible = false
        }
    }

    override fun createCenterPanel(): JComponent? {
        return panel {
            row("Name:") {
                projectNameField()
            }
            row("Key:") {
                projectKeyField().comment("A short identifier that is used to generate IDs for other objects that belong to this project.<br/>" +
                                              "Once the project has been created, the key cannot be changed.",
                                          "A short identifier that is used to generate IDs for other objects that belong to this project.<br/>".length
                )
            }
            row("Private:") {
                privateCheckbox().comment("A private project is only visible to its members")
            }
            row("Description") {
                scrollPane(projectDescriptionField)
            }
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
        projectNameField.text.let {
            if (it.length < 2 || it.length > 100) {
                list.add(ValidationInfo("Name should be between 2 and 100 characters long", projectKeyField))
            }
        }

        return list
    }

    override fun getDimensionServiceKey(): String = "circlet.vcs.share.CircletCreateProjectDialog"

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
