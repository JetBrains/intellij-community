package circlet.plugins.pipelines.services.execution

import circlet.pipelines.engine.*
import circlet.pipelines.engine.api.*
import circlet.plugins.pipelines.services.*
import circlet.plugins.pipelines.viewmodel.*
import com.intellij.execution.process.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import libraries.coroutines.extra.*
import libraries.klogging.*
import runtime.*
import java.io.*

class CircletTaskRunner(val project: Project) {

    companion object : KLogging()

    fun run(taskName: String): ProcessHandler {

        val script = project.service<SpaceKtsModelBuilder>().script.value
        val logData = LogData("")

        // todo: better lifetime.
        publishBuildLog(Lifetime.Eternal, project, logData)

        if (script == null) {
            //logData.add("Script is null")
            throw com.intellij.execution.ExecutionException("Script is null")
        }

        val config = script.config
        val task = script.config.jobs.firstOrNull { x -> x.name == taskName }
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

        // todo: better lifetime
        val provider = CircletIdeaStepExecutionProvider(Lifetime.Eternal, { text -> processHandler.println(text) }, { code -> processHandler.destroyProcess()}, storage)

        val tracer = CircletIdeaAutomationTracer()
        val automationGraphEngineCommon = AutomationGraphEngineImpl(
            provider,
            storage,
            provider,
            SystemTimeTicker(),
            tracer,
            listOf(provider))

        val automationStarterCommon = AutomationGraphManagerImpl(
            orgInfo,
            storage,
            CircletIdeaAutomationBootstrapper(),
            automationGraphEngineCommon,
            tracer
        )

        val repositoryData = RepositoryData("repoId", null)
        val branch = "myBranch"
        val commit = "myCommit"

        // todo: start asynchronous task. what is multi-threading policy?
        // todo: better lifetime
        // todo: why?
        async(Lifetime.Eternal, Ui) {
            val graphId = automationStarterCommon.createGraph(0L, repositoryData, branch, commit, task)
            automationStarterCommon.startGraph(graphId)
        }.invokeOnCompletion {
            if (it != null) {
                processHandler.notifyTextAvailable("Run task failed. ${it.message}$newLine", ProcessOutputTypes.STDERR)
            }
        }

        logData.add("Run task $taskName")
        processHandler.println("Run task $taskName")

        return processHandler
    }

    private val newLine: String = System.getProperty("line.separator", "\n")

    private fun ProcessHandler.println(text: String) {
        this.notifyTextAvailable("$text$newLine", ProcessOutputTypes.SYSTEM)

    }
}
