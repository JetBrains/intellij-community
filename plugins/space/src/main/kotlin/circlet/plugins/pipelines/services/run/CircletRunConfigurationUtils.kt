package circlet.plugins.pipelines.services.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.project.Project

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
