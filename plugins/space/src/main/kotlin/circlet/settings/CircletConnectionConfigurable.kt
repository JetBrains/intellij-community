package circlet.settings

import circlet.messages.*
import circlet.utils.*
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

    override fun isModified(): Boolean =
        serverUrlWithProtocol != project.getService<CircletProjectSettings>().currentState.serverUrl

    override fun getId(): String = "circlet.settings.connection"

    override fun getDisplayName(): String = CircletBundle.message("connection-configurable.display-name")

    override fun apply() {
        val serverUrl = serverUrlWithProtocol

        project.getService<CircletProjectSettings>().loadState(CircletProjectSettings.State(serverUrl))
        form.serverUrlField.text = serverUrl
    }

    override fun createComponent(): JComponent? = form.panel

    override fun reset() {
        form.serverUrlField.text = project.getService<CircletProjectSettings>().currentState.serverUrl
    }
}
