package circlet.settings

import circlet.messages.*
import com.intellij.openapi.options.*
import com.intellij.openapi.project.*
import javax.swing.*

class CircletConnectionConfigurable(private val project: Project) : SearchableConfigurable {
    private val form = CircletConnectionConfigurableForm()

    private val serverUrlWithProtocol: String
        get() {
            val serverUrl = form.serverUrlField.text.trim()

            if (serverUrl.isNotEmpty() && !serverUrl.contains("://")) {
                return "https://$serverUrl"
            }

            return serverUrl
        }

    private val projectKeyTrimmed: String get() = form.projectKeyField.text.trim()

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
        form.serverUrlField.text = serverUrl

        val projectKey = projectKeyTrimmed

        settings.projectKey.value = projectKey
        form.projectKeyField.text = projectKey
    }

    override fun createComponent(): JComponent? = form.panel

    override fun reset() {
        val settings = project.settings

        form.serverUrlField.text = settings.serverUrl.value
        form.projectKeyField.text = settings.projectKey.value
    }
}
