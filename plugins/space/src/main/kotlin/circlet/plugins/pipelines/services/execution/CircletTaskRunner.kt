package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.ExecutionStatus
import circlet.pipelines.common.api.GraphExecId
import circlet.pipelines.common.api.StepExecId
import circlet.pipelines.common.api.TraceLevel
import circlet.pipelines.engine.AutomationGraphEngineImpl
import circlet.pipelines.engine.AutomationGraphManagerImpl
import circlet.pipelines.engine.ThreadPoolStepExecutionScheduler
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.api.storage.AutomationStorageTransaction
import circlet.pipelines.provider.FailureChecker
import circlet.pipelines.provider.io.CommonFile
import circlet.pipelines.provider.local.*
import circlet.plugins.pipelines.services.SpaceKtsModelBuilder
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.asCoroutineDispatcher
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.LifetimeSource
import libraries.coroutines.extra.launch
import libraries.io.DaemonThreadFactory
import libraries.io.random.Random
import libraries.klogging.KLogging
import libraries.klogging.catch
import libraries.process.OSProcesses
import runtime.Ui
import java.io.File
import java.io.OutputStream
import java.nio.file.Paths
import java.util.concurrent.Executors

class CircletTaskRunner(val project: Project) {

    companion object : KLogging()

    private val ideaLocalRunnerLabel = "CircletTaskRunner_${Random.nextString(10)}"

    fun run(taskName: String): ProcessHandler {

        logger.info("Run task $taskName")

        // todo: terminate lifetime.
        val lifetime = LifetimeSource()

        val config = project.service<SpaceKtsModelBuilder>().script.value?.config?.value ?: throw ExecutionException("Script is null")

        val task = config.jobs.firstOrNull { x -> x.name == taskName } ?: throw ExecutionException("Task $taskName doesn't exist")

        val processHandler = TaskProcessHandler(taskName)

        lifetime.add {
            processHandler.dispose()
        }

        val storage = CircletIdeaExecutionProviderStorage()

        val orgUrlWrappedForDocker = { "" }

        val dockerEventsListener = LocalDockerEventListenerImpl(lifetime)

        val processes = OSProcesses("Space")

        val tmp = createTempDir()
        File(tmp, "system").mkdirs()

        val file = CommonFile(tmp.toPath())

        val paths = DockerInDockerPaths(file, file)

//      /mnt/space/system/circlet-agent.jar
//      /mnt/space/system/entrypoint")

        val vp = IdeaLocalVolumeProvider(Paths.get("/Users/sergey.shkredov/work/tmp"), paths)


        val tracer = CircletIdeaAutomationTracer(processHandler)
        val dockerFacade = DockerFacadeImpl(orgUrlWrappedForDocker, ideaLocalRunnerLabel, vp, dockerEventsListener, tracer, processes)

        val batchSize = 1

        val logMessageSink = object : LogMessagesSink {
            override fun invoke(
              graphExecutionId: GraphExecId,
              stepExecutionId: StepExecId,
              serviceExecutionId: Long?,
              batchIndex: Int,
              data: List<LogLine>
            ) {
                data.forEach {
                    processHandler.message(it.line, TraceLevel.INFO)
                }
            }
        }

        val reporting = LocalReportingImpl(lifetime, processes, LocalReporting.Settings(batchSize), logMessageSink)


        val failureChecker = object : FailureChecker {
            override fun check(tx: AutomationStorageTransaction, updates: Set<StepExecutionStatusUpdate>) {
            }
        }

        val statusHub = ThreadPoolStepExecutionScheduler(lifetime) // TODO: use correct scheduler

        val listener = object : GraphLifecycleListener {
            override fun onBeforeGraphStatusChanged(tx: AutomationStorageTransaction, events: Iterable<GraphStatusChangedEvent>) {
                events.forEach { ev ->
                    when (ev.newStatus) {
                        ExecutionStatus.FINISHED -> {
                            processHandler.message("ExecutionStatus.FINISHED", TraceLevel.INFO)
                            lifetime.terminate()
                        }
                        ExecutionStatus.TERMINATED -> {
                            processHandler.message("ExecutionStatus.TERMINATED", TraceLevel.INFO)
                            lifetime.terminate()
                        }
                        ExecutionStatus.FAILED -> {
                            processHandler.message("ExecutionStatus.FAILED", TraceLevel.INFO)
                            lifetime.terminate()
                        }
                        else -> {

                        }

                    }
                }
            }

            override fun onBeforeStepStatusChanged(tx: AutomationStorageTransaction, events: Iterable<StepStatusChangedEvent>) {
            }
        }

        val hub = ThreadPoolStepExecutionScheduler(lifetime) // TODO: use correct scheduler

        val dispatcher = Ui

        val stepExecutionProvider = CircletIdeaStepExecutionProvider(lifetime, vp, storage, dockerFacade, reporting, dispatcher, tracer, failureChecker, hub, listOf(listener))

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

            override fun subscribeOnExecution(lifetime: Lifetime, handler: suspend (StepExecutionData<*>) -> Unit) {
                TODO("Not yet implemented")
            }

            override fun subscribeOnTermination(lifetime: Lifetime, handler: suspend (StepExecutionData<*>) -> Unit) {
                TODO("Not yet implemented")
            }
        }

        val automationGraphEngineCommon = AutomationGraphEngineImpl(
            statusHub,
            tracer,
            //storage,
            //executionScheduler,
            //SystemTimeTicker(),
            //tracer,
            listOf(listener))

        val automationStarterCommon = AutomationGraphManagerImpl(
            storage,
            automationGraphEngineCommon,
            tracer
        )

        launch(Lifetime.Eternal, Ui) {
            try {
                storage("run-graph") {
                    val graph = automationStarterCommon.createGraph(this, task)
                    automationStarterCommon.startGraph(this, graph)
                }
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
