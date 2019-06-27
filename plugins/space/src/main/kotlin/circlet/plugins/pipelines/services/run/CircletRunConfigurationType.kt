package circlet.plugins.pipelines.services.run

import circlet.plugins.pipelines.utils.*
import com.intellij.execution.configurations.*
import com.intellij.openapi.project.*
import com.intellij.openapi.util.*

class CircletRunConfigurationType : SimpleConfigurationType(
    "CircletRunConfiguration",
    "Circlet Task", "Run Circlet Task", NotNullLazyValue.createValue { CircletIcons.mainIcon }) {

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return CircletRunConfiguration(project, this)
    }
}
