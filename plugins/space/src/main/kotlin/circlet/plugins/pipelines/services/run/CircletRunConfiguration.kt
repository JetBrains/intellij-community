package circlet.plugins.pipelines.services.run

import com.intellij.execution.*
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.*
import com.intellij.openapi.options.*
import com.intellij.openapi.project.*

class CircletRunConfiguration(project: Project, factory: ConfigurationFactory)
    : LocatableConfigurationBase<CircletRunTaskConfigurationOptions>(project, factory, "Run") {

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return CircletRunTaskConfigurationEditor()
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        return CircletRunTaskState(options, environment)
    }

    public override fun getOptions(): CircletRunTaskConfigurationOptions {
        return super.getOptions() as CircletRunTaskConfigurationOptions
    }
}
