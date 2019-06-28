package circlet.plugins.pipelines.services.run

import com.intellij.openapi.options.*
import com.intellij.ui.components.*
import javax.swing.*

class CircletRunTaskConfigurationEditor : SettingsEditor<CircletRunConfiguration>() {
    private val taskNameField = JBTextField()
    override fun resetEditorFrom(s: CircletRunConfiguration) {
        val options = s.options
        taskNameField.text = options.taskName
    }

    override fun createEditor(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.LINE_AXIS)
        panel.add(JLabel("Task name:"))
        panel.add(taskNameField)
        return panel
    }

    override fun applyEditorTo(s: CircletRunConfiguration) {
        val options = s.options
        options.taskName = taskNameField.text
    }
}
