package circlet.plugins.pipelines.services.run

import circlet.plugins.pipelines.services.execution.*
import com.intellij.execution.*
import com.intellij.execution.configurations.*
import com.intellij.execution.process.*
import com.intellij.execution.runners.*
import com.intellij.openapi.components.*

class CircletRunTaskState(private val settings: CircletRunTaskConfigurationOptions, environment: ExecutionEnvironment) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val project = environment.project
        val runner = project.service<CircletTaskRunner>()
        val taskName = settings.taskName
        if (taskName == null) {
            throw ExecutionException("TaskName is null")
        }
        else {
            return runner.run(taskName)
        }
    }

}
