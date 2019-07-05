package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.*
import circlet.pipelines.config.api.*
import circlet.pipelines.engine.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.api.storage.*
import libraries.klogging.*

class CircletIdeaGraphStorageTransaction(private val storage: CircletIdeaAutomationGraphStorage) : GraphStorageTransaction {

    private val callback = AfterTransactionCallback()

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
        return CircletIdeaAGraphMetaEntity(metaTaskId, storage.task)
    }

    override fun findJobExecution(id: Long): AJobExecutionEntity<*>? {
        return storage.storedExecutions[id]
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
            jobs
        )

        metaTask.originalMeta.jobs.flatten().forEach {
            if (it is ProjectJob.Process.Container) {
                jobs.add(createAJobExecutionEntity(it, graphExecutionEntity))
            } else {
                logger.warn { "${it::class} is not supported" }
            }
        }

        return graphExecutionEntity
    }

    override fun createSshKey(graphExecution: AGraphExecutionEntity, fingerPrint: String) {
        logger.debug { "createSshKey $fingerPrint" }
    }

    override fun batchCreateJobExecutions(graphExecution: AGraphExecutionEntity, bootstrapJob: ProjectJob.Process.Container) {
        if (graphExecution !is CircletIdeaAGraphExecutionEntity) {
            error("Unknown $graphExecution")
        }

        graphExecution.jobsList.add(
            createAJobExecutionEntity(bootstrapJob, graphExecution)
        )
        graphExecution.executionMeta = graphExecution.graph.originalMeta.prependJobs(listOf(bootstrapJob))
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
            JobStartContext()
        )
        storage.storedExecutions[jobExecId] = entity
        return entity
    }

    override fun findSnapshotForJobExecution(jobExec: AJobExecutionEntity<*>): AVolumeSnapshotEntity? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
