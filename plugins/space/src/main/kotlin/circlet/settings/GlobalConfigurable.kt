package circlet.settings

import circlet.messages.*
import com.intellij.openapi.options.*
import javax.swing.*

class GlobalConfigurable : SearchableConfigurable {
    private val form = GlobalConfigurableForm()

    override fun isModified(): Boolean = false

    override fun getId(): String = "circlet.settings.global"

    override fun getDisplayName(): String = CircletBundle.message("global-configurable.display-name")

    override fun apply() {
    }

    override fun createComponent(): JComponent? = form.panel
}
