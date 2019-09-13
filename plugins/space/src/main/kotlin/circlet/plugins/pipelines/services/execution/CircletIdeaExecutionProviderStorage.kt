package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.*
import circlet.pipelines.config.api.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.api.storage.*
import circlet.pipelines.provider.api.*
import libraries.klogging.*

class CircletIdeaExecutionProviderStorage(private val task: ProjectTask) : ExecutionProviderStorage {
    companion object : KLogging()

    private val idStorage = TaskLongIdStorage()
    private val storedExecutions = mutableMapOf<Long, AJobExecutionEntity<*>>()

    override fun findJobExecution(id: Long, forUpdate: Boolean): AJobExecutionEntity<ProjectJob.Process<*, *>>? {
        return storedExecutions[id]
    }

    override fun findJobExecutions(ids: List<Long>, forUpdate: Boolean): Sequence<AJobExecutionEntity<ProjectJob.Process<*, *>>> {
        return storedExecutions.filterKeys { it in ids }.map { it.value }.asSequence()
    }

    override fun findAuthClient(graphExecution: AGraphExecutionEntity): ServiceCredentials? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findSnapshotForJobExecution(jobExec: AJobExecutionEntity<*>): AVolumeSnapshotEntity? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAllNotFinishedJobs(graphExecution: AGraphExecutionEntity): Iterable<AJobExecutionEntity<ProjectJob.Process<*, *>>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findMetaTask(metaTaskId: Long): AGraphMetaEntity? {
        logger.debug { "findMetaTask $metaTaskId" }
        return CircletIdeaAGraphMetaEntity(metaTaskId, task)
    }

    override fun findExecution(id: Long): AGraphExecutionEntity? {
        TODO("findExecution not implemented")
    }

    override fun createTaskExecution(metaTask: AGraphMetaEntity, taskStartContext: TaskStartContext, bootstrapJobFactory: (AGraphExecutionEntity) -> ProjectJob.Process.Container?): AGraphExecutionEntity {
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

    override suspend fun <T> invoke(name: String, body: AutomationStorageTransaction.() -> T): T {
        val tx = Transaction()
        val result = tx.body()
        tx.executeAfterTransaction()

        return result
    }

    private fun createAJobExecutionEntity(bootstrapJob: ProjectJob.Process.Container, graphExecution: AGraphExecutionEntity): AJobExecutionEntity<*> {
        val jobExecId = idStorage.getOrCreateId(bootstrapJob.id)
        val entity = CircletIdeaAJobExecutionEntity(
            jobExecId,
            System.currentTimeMillis(),
            null,
            null,
            ExecutionStatus.SCHEDULED,
            graphExecution,
            bootstrapJob,
            null,
            false,
            null
        )
        storedExecutions[jobExecId] = entity
        return entity
    }

    private class Transaction : AutomationStorageTransaction {
        override fun timestamp(): Long {
            return System.currentTimeMillis()
        }

        private val hooks = mutableListOf<suspend () -> Unit>()

        override fun afterTransaction(priority: CallbackPriority, body: suspend () -> Unit) {
            hooks.add(body)
        }

        suspend fun executeAfterTransaction() {
            hooks.forEach { it() }
        }
    }
}
