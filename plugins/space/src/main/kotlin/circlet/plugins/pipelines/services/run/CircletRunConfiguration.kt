package circlet.plugins.pipelines.services.run

import com.intellij.execution.*
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.*
import com.intellij.openapi.options.*
import com.intellij.openapi.project.*
import org.jdom.*

class CircletRunConfiguration(project: Project, factory: ConfigurationFactory)
    : LocatableConfigurationBase<Element>(project, factory, "Run")  {
    val settings = CircletRunTaskConfigurationSettings()

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return CircletRunTaskConfigurationEditor()
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        return CircletRunTaskState(environment)
    }
}
