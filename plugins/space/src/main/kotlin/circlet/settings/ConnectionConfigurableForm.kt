package circlet.settings

import com.intellij.ui.components.*
import javax.swing.*

class ConnectionConfigurableForm {
    lateinit var panel: JPanel
        private set

    lateinit var serverUrlField: JBTextField
        private set

    lateinit var projectKeyField: JBTextField
        private set
}
