package circlet.plugins.pipelines.services.execution

import circlet.pipelines.engine.*
import circlet.pipelines.engine.api.*
import circlet.plugins.pipelines.services.*
import com.intellij.execution.process.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import libraries.coroutines.extra.*
import libraries.klogging.*
import runtime.*
import java.io.*
import com.intellij.execution.*

class CircletTaskRunner(val project: Project) {

    companion object : KLogging()

    fun run(taskName: String): ProcessHandler {

        val script = project.service<SpaceKtsModelBuilder>().script.value ?: throw ExecutionException("Script is null")

        val task = script.config.jobs.firstOrNull { x -> x.name == taskName } ?: throw ExecutionException("Task $taskName doesn't exist")

        logger.info("Run task $taskName")

        val processHandler = TaskProcessHandler(taskName)

        val storage = CircletIdeaExecutionProviderStorage(task)
        val orgInfo = OrgInfo("jetbrains.team")

        // todo: better lifetime
        val provider = CircletIdeaStepExecutionProvider(Lifetime.Eternal, { text -> processHandler.println(text) }, { _ -> processHandler.destroyProcess() }, storage)

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
        launch(Lifetime.Eternal, Ui) {
            try {
                val graphId = automationStarterCommon.createGraph(0L, repositoryData, branch, commit, task)
                automationStarterCommon.startGraph(graphId)
            } catch (th: Throwable) {
                processHandler.notifyTextAvailable("Run task failed. ${th.message}$newLine", ProcessOutputTypes.STDERR)
            } finally {
                processHandler.dispose()
            }
        }

        return processHandler
    }

    private val newLine: String = System.getProperty("line.separator", "\n")

    private fun ProcessHandler.println(text: String) {
        this.notifyTextAvailable("$text$newLine", ProcessOutputTypes.SYSTEM)

    }
}

class TaskProcessHandler(private val taskName: String) : ProcessHandler() {

    companion object : KLogging()

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

    fun dispose() {
        notifyProcessTerminated(0)
    }


}
