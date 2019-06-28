package circlet.plugins.pipelines.services.run

import circlet.plugins.pipelines.services.*
import circlet.plugins.pipelines.viewmodel.*
import com.intellij.execution.process.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import klogging.*
import java.io.*

class CircletTaskRunner(val project: Project) {

    companion object : KLogging()

    fun run(taskName: String): ProcessHandler {
        val circletModelStore = ServiceManager.getService(project, CircletModelStore::class.java)
        val viewModel = circletModelStore.viewModel
        val logData = LogData("")
        viewModel.logRunData.value = logData
        val script = viewModel.script.value
        if (script == null) {
            //logData.add("Script is null")
            throw com.intellij.execution.ExecutionException("Script is null")
        }

        val task = script.config.tasks.firstOrNull { x -> x.name == taskName }
        if (task == null) {
            //logData.add("Task $taskName doesn't exist")
            throw com.intellij.execution.ExecutionException("Task $taskName doesn't exist")
        }

        val stream = ByteArrayOutputStream()

        val processHandler = object : ProcessHandler() {
            override fun getProcessInput(): OutputStream? {
                logger.warn("getProcessInput")
                return stream
            }

            override fun detachIsDefault(): Boolean {
                logger.warn("detachIsDefault")
                return false
            }

            override fun detachProcessImpl() {
                logger.warn("detachProcessImpl")
                stream.write("detachProcessImpl".toByteArray())
            }

            override fun destroyProcessImpl() {
                logger.warn("destroyProcessImpl")
                stream.write("destroyProcessImpl".toByteArray())
                notifyProcessTerminated(0)
            }
        }

        viewModel.taskIsRunning.value = true
        logData.add("Run task $taskName")
        stream.write("Run task $taskName".toByteArray())
        viewModel.taskIsRunning.value = false
        return processHandler
    }
}
