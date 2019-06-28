package circlet.plugins.pipelines.services.run

import circlet.plugins.pipelines.services.*
import circlet.plugins.pipelines.viewmodel.*
import com.intellij.execution.process.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import libraries.klogging.*
import runtime.*
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

        logger.info("Run task $taskName")

        var timer : Cancellable? = null

        val processHandler = object : ProcessHandler() {
            override fun getProcessInput(): OutputStream? {
                return null
            }

            override fun detachIsDefault(): Boolean {
                return false
            }

            override fun detachProcessImpl() {
                logger.info("detachProcessImpl for task $taskName")
            }

            override fun destroyProcessImpl() {
                logger.info("destroyProcessImpl for task $taskName")
                notifyProcessTerminated(0)
                timer?.cancel()
            }
        }

        var messageCounter = 0
        val newLine = System.getProperty("line.separator", "\n")
        timer = UiDispatch.dispatchInterval(1000) {
            processHandler.notifyTextAvailable("Dummy message for task: $taskName. message #${messageCounter++}$newLine", ProcessOutputTypes.STDOUT)
        }

        viewModel.taskIsRunning.value = true
        logData.add("Run task $taskName")
        processHandler.notifyTextAvailable("Run task $taskName", ProcessOutputTypes.SYSTEM)
        viewModel.taskIsRunning.value = false
        return processHandler
    }
}
