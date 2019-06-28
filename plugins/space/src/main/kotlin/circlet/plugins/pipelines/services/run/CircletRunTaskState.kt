package circlet.plugins.pipelines.services.run

import com.intellij.execution.configurations.*
import com.intellij.execution.process.*
import com.intellij.execution.runners.*
import com.intellij.openapi.components.*

class CircletRunTaskState(private val settings: CircletRunTaskConfigurationOptions, environment: ExecutionEnvironment) : CommandLineState(environment) {
    override fun startProcess(): ProcessHandler {
        val runner = ServiceManager.getService(environment.project, CircletTaskRunner::class.java)
        val taskName = settings.taskName
        if (taskName == null) {
            throw com.intellij.execution.ExecutionException("TaskName is null")
        }
        else {
            return runner.run(taskName)
        }
    }
}
