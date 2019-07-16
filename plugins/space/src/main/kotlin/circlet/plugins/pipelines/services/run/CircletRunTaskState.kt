package circlet.plugins.pipelines.services.run

import circlet.plugins.pipelines.services.*
import circlet.plugins.pipelines.services.execution.*
import com.intellij.execution.configurations.*
import com.intellij.execution.process.*
import com.intellij.execution.runners.*
import com.intellij.openapi.components.*

class CircletRunTaskState(private val settings: CircletRunTaskConfigurationOptions, environment: ExecutionEnvironment) : CommandLineState(environment) {
    override fun startProcess(): ProcessHandler {
        val project = environment.project
        val runner = ServiceManager.getService(project, CircletTaskRunner::class.java)
        val circletModelStore = ServiceManager.getService(project, CircletModelStore::class.java)
        val lifetime = circletModelStore.viewModel.script.value?.lifetime ?: throw com.intellij.execution.ExecutionException("Model == null")
        val taskName = settings.taskName
        if (taskName == null) {
            throw com.intellij.execution.ExecutionException("TaskName is null")
        }
        else {
            return runner.run(lifetime, taskName)
        }
    }
}
