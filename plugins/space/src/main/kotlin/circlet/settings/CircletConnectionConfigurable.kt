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

    override fun isModified(): Boolean = serverUrlWithProtocol.isNotEmpty()

    override fun getId(): String = "circlet.settings.connection"

    override fun getDisplayName(): String = CircletBundle.message("connection-configurable.display-name")

    override fun apply() {
        project
    }

    override fun createComponent(): JComponent? = form.panel

    override fun reset() {
        form.serverUrlField.text = ""
    }
}
