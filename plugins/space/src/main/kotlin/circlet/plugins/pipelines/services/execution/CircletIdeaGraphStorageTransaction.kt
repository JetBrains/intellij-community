package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.*
import circlet.pipelines.config.api.*
import circlet.pipelines.engine.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.api.storage.*
import circlet.pipelines.provider.api.*
import libraries.klogging.*

class CircletIdeaGraphStorageTransaction(private val storage: CircletIdeaAutomationGraphStorage) : ExecutionProviderTransaction {

    private val callback = AfterTransactionCallback()

    companion object : KLogging()

    suspend fun executeAfterTransactionHooks() {
        callback.run()
    }

    override fun executeAfterTransaction(body: suspend () -> Unit) {
        logger.debug { "executeAfterTransaction" }
        callback.afterTransaction(body)
    }

    override fun andThen(body: () -> Unit) {
        executeAfterTransaction { body() }
    }

    override fun getAllNotFinishedJobs(graphExecution: AGraphExecutionEntity): Iterable<AJobExecutionEntity<ProjectJob.Process<*, *>>> {
        TODO("getAllNotFinishedJobs not implemented")
    }

    override fun findMetaTask(metaTaskId: Long): AGraphMetaEntity? {
        logger.debug { "findMetaTask $metaTaskId" }
        return CircletIdeaAGraphMetaEntity(metaTaskId, storage.task)
    }

    override fun createTaskExecution(
        metaTask: AGraphMetaEntity,
        taskStartContext: TaskStartContext,
        bootstrapJobFactory: (AGraphExecutionEntity) -> ProjectJob.Process.Container?
    ): AGraphExecutionEntity {
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
            "master",
            "commit-hash",
            jobs
        )

        metaTask.originalMeta.jobs.flatten().forEach {
            if (it is ProjectJob.Process.Container) {
                jobs.add(createAJobExecutionEntity(it, graphExecutionEntity))
            } else {
                logger.warn { "${it::class} is not supported" }
            }
        }

        val bootstrapJob = bootstrapJobFactory(graphExecutionEntity)
        if (bootstrapJob != null) {
            graphExecutionEntity.jobsList.add(
                createAJobExecutionEntity(bootstrapJob, graphExecutionEntity)
            )
            graphExecutionEntity.executionMeta = graphExecutionEntity.graphMeta.originalMeta.prependJobs(listOf(bootstrapJob))
        }

        return graphExecutionEntity
    }

    private fun createAJobExecutionEntity(bootstrapJob: ProjectJob.Process.Container, graphExecution: AGraphExecutionEntity): AJobExecutionEntity<*> {
        val jobExecId = storage.idStorage.getOrCreateId(bootstrapJob.id)
        val entity = CircletIdeaAJobExecutionEntity(
            jobExecId,
            System.currentTimeMillis(),
            null,
            null,
            ExecutionStatus.SCHEDULED,
            graphExecution,
            bootstrapJob,
            false,
            null,
            false,
            null
        )
        storage.storedExecutions[jobExecId] = entity
        return entity
    }

    override fun findJobExecution(id: Long, forUpdate: Boolean): AJobExecutionEntity<ProjectJob.Process<*, *>>? {
        return storage.storedExecutions[id]
    }

    override fun findJobExecutions(ids: List<Long>, forUpdate: Boolean): Sequence<AJobExecutionEntity<ProjectJob.Process<*, *>>> {
        return storage.storedExecutions.filterKeys { it in ids }.map { it.value }.asSequence()
    }

    override fun findAuthClient(graphExecution: AGraphExecutionEntity): ServiceCredentials? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findSnapshotForJobExecution(jobExec: AJobExecutionEntity<*>): AVolumeSnapshotEntity? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
