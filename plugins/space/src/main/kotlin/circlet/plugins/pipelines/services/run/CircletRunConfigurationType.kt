package circlet.plugins.pipelines.services.run

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import icons.SpaceIcons
import platform.common.ProductName

class CircletRunConfigurationType : SimpleConfigurationType(
    "CircletRunConfiguration",
    "$ProductName Task",
    "Run $ProductName Task",
    NotNullLazyValue.createValue { SpaceIcons.Main }) {

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return CircletRunConfiguration(project, this)
    }

    override fun getOptionsClass(): Class<out BaseState>? {
        return CircletRunTaskConfigurationOptions::class.java
    }
}
