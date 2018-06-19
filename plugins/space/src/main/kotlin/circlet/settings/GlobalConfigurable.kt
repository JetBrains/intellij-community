package circlet.settings

import circlet.messages.*
import com.intellij.openapi.options.*
import com.intellij.uiDesigner.core.*
import javax.swing.*

class GlobalConfigurable : SearchableConfigurable {
    private val panel = JPanel(GridLayoutManager(1, 1))

    override fun isModified(): Boolean = false

    override fun getId(): String = "circlet.settings.global"

    override fun getDisplayName(): String = CircletBundle.message("global-configurable.display-name")

    override fun apply() {
    }

    override fun createComponent(): JComponent? = panel
}
