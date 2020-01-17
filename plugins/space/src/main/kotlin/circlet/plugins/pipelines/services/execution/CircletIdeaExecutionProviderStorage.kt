package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.*
import circlet.pipelines.config.api.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.api.storage.*
import circlet.pipelines.provider.api.*
import circlet.pipelines.provider.local.*
import libraries.io.random.*
import libraries.klogging.*

class CircletIdeaExecutionProviderStorage : LocalExecutionProviderStorage {
    companion object : KLogging()

    private val idStorage = TaskLongIdStorage()
    private val storedStepExecutions = mutableMapOf<Long, AStepExecutionEntity<*>>()

    val executions = mutableListOf<CircletIdeaAGraphExecutionEntity>()
    val volumeSnapshots = mutableListOf<CircletIdeaVolumeSnapshotEntity>()

    override fun findVolumeId(volumeName: String): String? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun registerVolume(graphExecution: AGraphExecutionEntity, volumeId: String, volumeName: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun deleteVolumesByGraphExecution(graphExecution: AGraphExecutionEntity): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun registerSnapshot(snapshotId: String, stepExecutionId: Long) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun deleteSnapshotsByGraphExecution(graphExecution: AGraphExecutionEntity): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findRunningJobsWithWorkerId(limit: Int): Sequence<AStepExecutionEntity<*>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findStepExecution(id: Long, forUpdate: Boolean): AStepExecutionEntity<ScriptStep.Process<*, *>>? {
        return storedStepExecutions[id]
    }

    override fun findStepExecutions(ids: List<Long>, forUpdate: Boolean): Sequence<AStepExecutionEntity<ScriptStep.Process<*, *>>> {
        return storedStepExecutions.filterKeys { it in ids }.map { it.value }.asSequence()
    }

    override fun findAuthClient(graphExecution: AGraphExecutionEntity): ServiceCredentials? {
        // empty creds here.
        return ServiceCredentials("", "")
    }

    override fun findSnapshotForJobExecution(stepExec: AStepExecutionEntity<*>): AVolumeSnapshotEntity? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findSnapshotCreationPoint(graphExecutionId: Long): AStepExecutionEntity<ScriptStep.Process<*, *>>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findGraphExecutionById(graphExecutionId: Long): AGraphExecutionEntity? {
        return executions.firstOrNull { it.id == graphExecutionId }
    }

    override fun createGraphExecution(
        repoName: String,
        branch: String,
        commit: CommitHash,
        actionMeta: ScriptAction,
        bootstrapStepFactory: (AGraphExecutionEntity) -> ScriptStep.Process.Container?): AGraphExecutionEntity {

        logger.debug { "createJobExecution $actionMeta" }

        val now = System.currentTimeMillis()
        val jobs = mutableListOf<AStepExecutionEntity<*>>()

        val graphContext = CircletIdeaAGraphExecutionContextEntity(
            branch,
            commit,
            repoName
        )

        val graphExecutionEntity = CircletIdeaAGraphExecutionEntity(
            Random.nextLong(),
            now,
            null,
            ExecutionStatus.PENDING,
            actionMeta,
            graphContext,
            jobs
        )

        actionMeta.steps.flatten().forEach {
            if (it is ScriptStep.Process.Container) {
                jobs.add(createAStepExecutionEntity(it, graphExecutionEntity))
            }
            else {
                logger.warn { "${it::class} is not supported" }
            }
        }

        val bootstrapStep = bootstrapStepFactory(graphExecutionEntity)

        if (bootstrapStep != null) {
            graphExecutionEntity.jobsList.add(
                createAStepExecutionEntity(bootstrapStep, graphExecutionEntity)
            )
            graphExecutionEntity.executionMeta = graphExecutionEntity.executionMeta.prependSteps(listOf(bootstrapStep))
        }

        executions.add(graphExecutionEntity)

        return graphExecutionEntity
    }

    override suspend fun <T> invoke(name: String, body: AutomationStorageTransaction.() -> T): T {
        val tx = Transaction()
        val result = tx.body()
        tx.executeAfterTransaction()

        return result
    }

    private fun createAStepExecutionEntity(bootstrapJob: ScriptStep.Process.Container, graphExecution: AGraphExecutionEntity): AStepExecutionEntity<*> {
        val jobExecId = idStorage.getOrCreateId(bootstrapJob.id)
        val entity = CircletIdeaAContainerStepExecutionEntity(
            jobExecId,
            System.currentTimeMillis(),
            null,
            ExecutionStatus.SCHEDULED,
            graphExecution,
            bootstrapJob,
            null,
            false,
            null
        )
        storedStepExecutions[jobExecId] = entity
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
