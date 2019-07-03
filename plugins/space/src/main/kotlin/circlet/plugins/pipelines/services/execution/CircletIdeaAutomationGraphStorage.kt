package circlet.plugins.pipelines.services.execution

import circlet.pipelines.config.api.*
import circlet.pipelines.engine.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.storage.*
import circlet.pipelines.engine.utils.*
import circlet.pipelines.utils.*
import libraries.klogging.*

//copypasted from server. todo remove
class AfterTransactionCallback {
    private val hooks = mutableListOf<suspend () -> Unit>()

    fun afterTransaction(body: suspend () -> Unit) {
        hooks.add(body)
    }

    suspend fun run() {
        hooks.forEach { it() }
    }
}

class CircletIdeaAGraphMetaEntity(
    override val id: Long,
    override val originalMeta: ProjectAction) : AGraphMetaEntity

class CircletIdeaAGraphExecutionEntity(
    override val id: Long,
    override var triggerTime: Long,
    override var startTime: Long?,
    override var endTime: Long?,
    override var status: ExecutionStatus,
    override val graph: AGraphMetaEntity,
    override var executionMeta: ProjectAction,
    private val jobsList: List<AJobExecutionEntity<*>>) : AGraphExecutionEntity {

    override val jobs: Sequence<AJobExecutionEntity<*>>
        get() = jobsList.asSequence()
}


class CircletIdeaAJobExecutionEntity(
    override val id: Long,
    override var triggerTime: Long,
    override var startTime: Long?,
    override var endTime: Long?,
    override var status: ExecutionStatus,
    override val graph: AGraphExecutionEntity,
    override val meta: ProjectJob.Process.Container,
    override val context: JobStartContext)
    : AContainerExecutionEntity


class TaskLongIdStorage {
    private var nextId : Long = 0
    private val map = mutableMapOf<String, Long>()
    fun getOrCreateId(stringId: String) : Long {
        return map.getOrPut(stringId) {
            nextId++
        }
    }
}

class CircletIdeaGraphStorageTransaction(private val task: ProjectTask) : GraphStorageTransaction {

    private val callback = AfterTransactionCallback()
    private val idStorage = TaskLongIdStorage()

    companion object : KLogging()

    suspend fun executeAfterTransactionHooks() {
        callback.run()
    }

    override fun executeAfterTransaction(body: suspend () -> Unit) {
        logger.debug { "executeAfterTransaction" }
        callback.afterTransaction(body)
    }

    override fun getAllNotFinishedJobs(graphExecution: AGraphExecutionEntity): Iterable<AJobExecutionEntity<*>> {
        TODO("getAllNotFinishedJobs not implemented")
    }

    override fun findMetaTask(metaTaskId: Long): AGraphMetaEntity? {
        logger.debug { "findMetaTask $metaTaskId" }
        return CircletIdeaAGraphMetaEntity(metaTaskId, task)
    }

    override fun createTaskExecution(metaTask: AGraphMetaEntity, taskStartContext: TaskStartContext): AGraphExecutionEntity {
        logger.debug { "createTaskExecution $metaTask" }
        val now = System.currentTimeMillis()
        val jobs = mutableListOf<AJobExecutionEntity<*>>()
        val graphExecutionEntity = CircletIdeaAGraphExecutionEntity(
            metaTask.id,
            now,
            null,
            null,
            ExecutionStatus.PENDING,
            metaTask,
            metaTask.originalMeta,
            jobs)

        metaTask.originalMeta.jobs.traverseJobs {
            jobs.add(CircletIdeaAJobExecutionEntity(
                idStorage.getOrCreateId(it.id),
                now,
                null,
                null,
                ExecutionStatus.SCHEDULED,
                graphExecutionEntity,
                it,
                JobStartContext()))

        }

        return graphExecutionEntity
    }

    private fun ProjectJob.traverseJobs(action: (ProjectJob.Process.Container) -> Unit) {
        when (val job = this) {
            is ProjectJob.CompositeJob -> {
                job.children.forEach {
                    it.traverseJobs(action)
                }
            }
            is ProjectJob.Process.Container -> {
                action(job)
            }
            is ProjectJob.Process.VM -> {
                logger.warn { "ProjectJob.Process.VM is not supported" }
            }
        }
    }

    override fun createSshKey(graphExecution: AGraphExecutionEntity, fingerPrint: String) {
        logger.debug { "createSshKey $fingerPrint" }
    }

    override fun batchCreateJobExecutions(graphExecution: AGraphExecutionEntity, bootstrapJob: ProjectJob.Process.Container): Sequence<AJobExecutionEntity<*>> {
        val res = mutableListOf<AJobExecutionEntity<*>>()
        res.addAll(graphExecution.jobs)
        res.add(CircletIdeaAJobExecutionEntity(
            idStorage.getOrCreateId(bootstrapJob.id),
            System.currentTimeMillis(),
            null,
            null,
            ExecutionStatus.SCHEDULED,
            graphExecution,
            bootstrapJob,
            JobStartContext()))

        graphExecution.executionMeta = graphExecution.graph.originalMeta.prependJobs(listOf(bootstrapJob))
        return res.asSequence()
    }
}

class CircletIdeaAutomationGraphStorage(private val task: ProjectTask) : AutomationGraphStorage<CircletIdeaGraphStorageTransaction> {
    override suspend fun <T> invoke(body: (CircletIdeaGraphStorageTransaction) -> T): T {
        val tx = CircletIdeaGraphStorageTransaction(task)
        val res = body(tx)
        tx.executeAfterTransactionHooks()
        return res
    }
}

class CircletIdeaAutomationBootstrapper: AutomationBootstrapper {
    override fun createBootstrapJob(execution: AGraphExecutionEntity, repository: RepositoryData, orgUrl: String): ProjectJob.Process.Container {
        val container = ProjectJob.Process.Container(
            "hello-world",
            ProjectJob.ProcessData(
                exec = ProjectJob.ProcessExecutable.ContainerExecutable.DefaultCommand(emptyList())))
        container.applyIds()
        return container
    }
}

class CircletIdeaAutomationTracer : AutomationTracer {

    companion object : KLogging()

    override fun trace(commit: CommitHash, message: String) {
        logger.debug { "$commit $message" }
    }

    override fun trace(executionId: Long, message: String) {
        logger.debug { "$executionId $message" }
    }
}

class CircletIdeaJobExecutionProvider : JobExecutionProvider<CircletIdeaGraphStorageTransaction> {

    companion object : KLogging()

    private var savedHandler: ((tx: CircletIdeaGraphStorageTransaction, job: AJobExecutionEntity<*>, newStatus: ExecutionStatus) -> Unit)? = null

    override fun scheduleExecution(tx: CircletIdeaGraphStorageTransaction, jobs: Iterable<AJobExecutionEntity<*>>) {
        TODO("scheduleExecution not implemented")
    }

    override fun scheduleTermination(jobs: Iterable<AJobExecutionEntity<*>>) {
        TODO("scheduleTermination not implemented")
    }

    override fun subscribeIdempotently(handler: (tx: CircletIdeaGraphStorageTransaction, job: AJobExecutionEntity<*>, newStatus: ExecutionStatus) -> Unit) {
        if (savedHandler != null) {
            logger.warn { "subscribeIdempotently. savedHandler != null"}
        }
        this.savedHandler = handler
    }

    override fun onBeforeGraphStatusChanged(tx: CircletIdeaGraphStorageTransaction, entity: AGraphExecutionEntity, oldStatus: ExecutionStatus, newStatus: ExecutionStatus) {
        TODO("onBeforeGraphStatusChanged not implemented")
    }

    override fun onBeforeJobStatusChanged(tx: CircletIdeaGraphStorageTransaction, entity: AJobExecutionEntity<*>, oldStatus: ExecutionStatus, newStatus: ExecutionStatus) {
        TODO("onBeforeJobStatusChanged not implemented")
    }

}

class SystemTimeTicker : Ticker {
    override val now: Long get() = System.currentTimeMillis()
}
