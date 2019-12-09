package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.*
import circlet.pipelines.config.api.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.api.storage.*
import circlet.pipelines.provider.api.*
import libraries.klogging.*

class CircletIdeaExecutionProviderStorage(private val job: ScriptJob) : ExecutionProviderStorage {
    companion object : KLogging()

    private val idStorage = TaskLongIdStorage()
    private val storedExecutions = mutableMapOf<Long, AStepExecutionEntity<*>>()

    override fun findJobExecution(id: Long, forUpdate: Boolean): AStepExecutionEntity<ScriptStep.Process<*, *>>? {
        return storedExecutions[id]
    }

    override fun findJobExecutions(ids: List<Long>, forUpdate: Boolean): Sequence<AStepExecutionEntity<ScriptStep.Process<*, *>>> {
        return storedExecutions.filterKeys { it in ids }.map { it.value }.asSequence()
    }

    override fun findAuthClient(graphExecution: AGraphExecutionEntity): ServiceCredentials? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findSnapshotForJobExecution(stepExec: AStepExecutionEntity<*>): AVolumeSnapshotEntity? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findSnapshotCreationPoint(graphExecutionId: Long): AStepExecutionEntity<ScriptStep.Process<*, *>>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findGraphMeta(graphMetaId: Long): AGraphMetaEntity? {
        logger.debug { "findMetaTask $graphMetaId" }
        return CircletIdeaAGraphMetaEntity(graphMetaId, job)
    }

    override fun findGraphExecutionByJobActionExecutionId(id: Long): AGraphExecutionEntity? {
        TODO("findExecution not implemented")
    }

    override fun createJobExecution(metaTask: AGraphMetaEntity, jobStartContext: ActionStartContext, bootstrapJobFactory: (AGraphExecutionEntity) -> ScriptStep.Process.Container?): AGraphExecutionEntity {
        logger.debug { "createTaskExecution $metaTask" }
        val now = System.currentTimeMillis()
        val jobs = mutableListOf<AStepExecutionEntity<*>>()
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
            "repoId",
            jobs
        )

        metaTask.originalMeta.jobs.flatten().forEach {
            if (it is ScriptStep.Process.Container) {
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

    private fun createAJobExecutionEntity(bootstrapJob: ScriptStep.Process.Container, graphExecution: AGraphExecutionEntity): AStepExecutionEntity<*> {
        val jobExecId = idStorage.getOrCreateId(bootstrapJob.id)
        val entity = CircletIdeaAContainerStepExecutionEntity(
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
        private var executed = false

        override fun afterTransaction(priority: CallbackPriority, body: suspend () -> Unit) {
            if (executed) {
                error("transaction has been already executed")
            }
            hooks.add(body)
        }

        suspend fun executeAfterTransaction() {
            executed = true
            hooks.forEach { it() }
        }
    }
}
