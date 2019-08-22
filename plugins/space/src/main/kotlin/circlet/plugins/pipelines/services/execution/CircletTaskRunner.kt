package circlet.plugins.pipelines.services.execution

import circlet.pipelines.engine.*
import circlet.pipelines.engine.api.*
import circlet.plugins.pipelines.services.*
import circlet.plugins.pipelines.viewmodel.*
import com.intellij.execution.process.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import libraries.klogging.*
import runtime.*
import runtime.async.*
import runtime.reactive.*
import java.io.*

class CircletTaskRunner(val project: Project) {

    companion object : KLogging()

    fun run(lifetime: Lifetime, taskName: String): ProcessHandler {
        val circletModelStore = ServiceManager.getService(project, CircletModelStore::class.java)
        val viewModel = circletModelStore.viewModel
        val logData = LogData("")
        viewModel.logRunData.value = logData
        val script = viewModel.script.value
        if (script == null) {
            //logData.add("Script is null")
            throw com.intellij.execution.ExecutionException("Script is null")
        }

        val config = script.config
        val task = script.config.tasks.firstOrNull { x -> x.name == taskName }
        if (task == null) {
            //logData.add("Task $taskName doesn't exist")
            throw com.intellij.execution.ExecutionException("Task $taskName doesn't exist")
        }

        logger.info("Run task $taskName")

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
            }
        }

        val storage = CircletIdeaExecutionProviderStorage(task)
        val orgInfo = OrgInfo("jetbrains.team")
        val provider = CircletIdeaJobExecutionProvider(lifetime, { text -> processHandler.println(text) }, { code -> processHandler.destroyProcess()}, storage)
        val tracer = CircletIdeaAutomationTracer()
        val automationGraphEngineCommon = AutomationGraphEngineImpl(
            provider,
            storage,
            provider,
            SystemTimeTicker(),
            tracer,
            listOf(provider))
        val automationStarterCommon = AutomationStarterImpl(
            orgInfo,
            storage,
            CircletIdeaAutomationBootstrapper(),
            automationGraphEngineCommon,
            tracer
        )

        val currentTime = System.currentTimeMillis()
        val metaTaskId : Long = 1
        val principalId : Long = 1
        val projectKey = "myProjectKey"
        val repositoryData = RepositoryData("repoId", null)
        val branch = "myBranch"
        val commit = "myCommit"
        val trigger = TriggerData.ManualTriggerData(currentTime, principalId)
        val context = TaskStartContext(projectKey, repositoryData, branch, commit, emptyList(), trigger)

        async(lifetime, Ui) {
            automationStarterCommon.startTask(metaTaskId, context)
        }.invokeOnCompletion {
            if (it != null) {
                processHandler.notifyTextAvailable("Run task failed. ${it.message}$newLine", ProcessOutputTypes.STDERR)
            }
        }

        viewModel.taskIsRunning.value = true
        logData.add("Run task $taskName")
        processHandler.println("Run task $taskName")
        viewModel.taskIsRunning.value = false
        return processHandler
    }

    private val newLine: String = System.getProperty("line.separator", "\n")

    private fun ProcessHandler.println(text: String) {
        this.notifyTextAvailable("$text$newLine", ProcessOutputTypes.SYSTEM)

    }
}
