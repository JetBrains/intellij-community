package circlet.settings

import circlet.messages.*
import com.intellij.openapi.options.*
import com.intellij.openapi.project.*
import com.intellij.ui.components.*
import com.intellij.uiDesigner.core.*
import javax.swing.*

class ConnectionConfigurable(private val project: Project) : SearchableConfigurable {
    private val panel = JPanel(GridLayoutManager(3, 2))

    private val serverUrlField = JBTextField()
    private val projectKeyField = JBTextField()

    private val serverUrlWithProtocol: String
        get() {
            val serverUrl = serverUrlField.text.trim()

            if (serverUrl.isNotEmpty() && !serverUrl.contains("://")) {
                return "https://$serverUrl"
            }

            return serverUrl
        }

    private val projectKeyTrimmed: String get() = projectKeyField.text.trim()

    init {
        panel.add(
            JBLabel(CircletBundle.message("connection-configurable.server-url-label")).apply {
                labelFor = serverUrlField
            },
            GridConstraints().apply {
                row = 0
                column = 0
                vSizePolicy = GridConstraints.SIZEPOLICY_FIXED
                hSizePolicy = GridConstraints.SIZEPOLICY_FIXED
                anchor = GridConstraints.ANCHOR_WEST
            }
        )
        panel.add(
            serverUrlField,
            GridConstraints().apply {
                row = 0
                column = 1
                vSizePolicy = GridConstraints.SIZEPOLICY_FIXED
                fill = GridConstraints.FILL_HORIZONTAL
            }
        )

        panel.add(
            JBLabel(CircletBundle.message("connection-configurable.project-key-label")).apply {
                labelFor = projectKeyField
            },
            GridConstraints().apply {
                row = 1
                column = 0
                vSizePolicy = GridConstraints.SIZEPOLICY_FIXED
                hSizePolicy = GridConstraints.SIZEPOLICY_FIXED
                anchor = GridConstraints.ANCHOR_WEST
            }
        )
        panel.add(
            projectKeyField,
            GridConstraints().apply {
                row = 1
                column = 1
                vSizePolicy = GridConstraints.SIZEPOLICY_FIXED
                fill = GridConstraints.FILL_HORIZONTAL
            }
        )

        panel.add(
            Spacer(),
            GridConstraints().apply {
                row = 2
                column = 1
                vSizePolicy = GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_WANT_GROW
                hSizePolicy = GridConstraints.SIZEPOLICY_FIXED
                fill = GridConstraints.FILL_VERTICAL
            }
        )
    }

    override fun isModified(): Boolean {
        val settings = project.settings

        return serverUrlWithProtocol != settings.serverUrl.value || projectKeyTrimmed != settings.projectKey.value
    }

    override fun getId(): String = "circlet.settings.connection"

    override fun getDisplayName(): String = CircletBundle.message("connection-configurable.display-name")

    override fun apply() {
        val settings = project.settings

        val serverUrl = serverUrlWithProtocol

        settings.serverUrl.value = serverUrl
        serverUrlField.text = serverUrl

        val projectKey = projectKeyTrimmed

        settings.projectKey.value = projectKey
        projectKeyField.text = projectKey
    }

    override fun createComponent(): JComponent? = panel

    override fun reset() {
        val settings = project.settings

        serverUrlField.text = settings.serverUrl.value
        projectKeyField.text = settings.projectKey.value
    }
}
