package circlet.plugins.pipelines.services.run

import com.intellij.execution.*
import com.intellij.execution.executors.*
import com.intellij.openapi.project.*

object CircletRunConfigurationUtils {
    fun run(taskName: String, project: Project) {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.createConfiguration("Run task $taskName", CircletRunConfigurationType::class.java)
        val configuration = settings.configuration as CircletRunConfiguration
        configuration.options.taskName = taskName
        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }
}
