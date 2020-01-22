package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.*
import circlet.pipelines.engine.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.api.storage.*
import circlet.pipelines.provider.*
import circlet.pipelines.provider.io.*
import circlet.pipelines.provider.local.*
import circlet.pipelines.provider.local.docker.*
import circlet.plugins.pipelines.services.*
import com.intellij.execution.process.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import libraries.coroutines.extra.*
import libraries.klogging.*
import runtime.*
import java.io.*
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessHandler
import kotlinx.coroutines.*
import libraries.io.*
import libraries.io.random.*
import libraries.process.*
import java.nio.file.*
import java.util.concurrent.*

class CircletTaskRunner(val project: Project) {

    val ideaLocalRunnerLabel = "CircletTaskRunner_${Random.nextString(10)}"

    companion object : KLogging()

    fun run(taskName: String): ProcessHandler {

        logger.info("Run task $taskName")

        // todo: terminate lifetime.
        val lifetime = LifetimeSource()

        val config = project.service<SpaceKtsModelBuilder>().script.value?.config?.value ?: throw ExecutionException("Script is null")

        val task = config.jobs.firstOrNull { x -> x.name == taskName } ?: throw ExecutionException("Task $taskName doesn't exist")

        val processHandler = TaskProcessHandler(taskName)

        val storage = CircletIdeaExecutionProviderStorage()

        val orgInfo = OrgInfo("jetbrains.team")

        val paths = DockerInDockerPaths(
            CommonFile(Files.createTempDirectory("circlet")),
            CommonFile(Files.createTempDirectory("circlet"))
        )

        val volumeProvider = LocalVolumeProvider(paths)

        val orgUrlWrappedForDocker = {
            ""
        }

        val dockerEventsListener = LocalDockerEventListenerImpl(lifetime)

        val processes = OSProcesses("Space")

        val dockerFacade = DockerFacadeImpl(orgUrlWrappedForDocker, ideaLocalRunnerLabel, volumeProvider, dockerEventsListener, processes)

        val batchSize = 1

        val logMessageSink = object : LogMessagesSink {
            override fun invoke(stepExecutionData: StepExecutionData<*>, batchIndex: Int, data: List<String>) {
                data.forEach {
                    processHandler.message(it, TraceLevel.INFO)
                }
            }
        }

        val reporting = LocalReportingImpl(lifetime, processes, LocalReporting.Settings(batchSize), logMessageSink)

        val dispatcher = Ui

        val tracer = CircletIdeaAutomationTracer(processHandler)

        val failureChecker = object : FailureChecker {
            override fun check(tx: AutomationStorageTransaction, updates: Set<StepExecutionStatusUpdate>) {
            }
        }

        val stepExecutionProvider = CircletIdeaStepExecutionProvider(lifetime, storage, volumeProvider, dockerFacade, reporting, dispatcher, tracer, failureChecker)

        val executionScheduler = object : StepExecutionScheduler {

            private val jobsSchedulingDispatcher = Executors.newFixedThreadPool(1, DaemonThreadFactory("Space Automation")).asCoroutineDispatcher()

            override fun scheduleExecution(stepExecs: Iterable<StepExecutionData<*>>) {
                launch(lifetime, jobsSchedulingDispatcher, "ServerJobExecutionScheduler:scheduleExecution") {
                    logger.catch {
                        stepExecs.forEach {
                            logger.info { "scheduleExecution ${it.meta.data.exec}" }
                            stepExecutionProvider.startExecution(it)
                        }
                    }
                }
            }

            override fun scheduleTermination(stepExecs: Iterable<StepExecutionData<*>>) {
                launch(lifetime, jobsSchedulingDispatcher, "ServerJobExecutionScheduler:scheduleTermination") {
                    logger.catch {
                        stepExecs.forEach {
                            stepExecutionProvider.startTermination(it)
                        }
                    }
                }
            }
        }


        val automationGraphEngineCommon = AutomationGraphEngineImpl(
            stepExecutionProvider,
            storage,
            executionScheduler,
            SystemTimeTicker(),
            tracer,
            listOf(stepExecutionProvider))

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
                val graphId = automationStarterCommon.createGraph(0L, repositoryData, branch, commit, task, bootstrapJobRequired = false)
                automationStarterCommon.startGraph(graphId)
            } catch (th: Throwable) {
                logger.error(th)
                processHandler.notifyTextAvailable("Run task failed. ${th.message}$newLine", ProcessOutputTypes.STDERR)
            }
        }

        return processHandler
    }

    private val newLine: String = System.getProperty("line.separator", "\n")
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
