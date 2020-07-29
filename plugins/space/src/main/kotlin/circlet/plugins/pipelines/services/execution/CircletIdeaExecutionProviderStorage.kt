package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.ExecutionStatus
import circlet.pipelines.common.api.GraphExecId
import circlet.pipelines.common.api.StepExecId
import circlet.pipelines.config.api.ScriptAction
import circlet.pipelines.config.api.ScriptStep
import circlet.pipelines.config.api.flatten
import circlet.pipelines.engine.api.storage.*
import circlet.pipelines.messages.TextMessageSeverity
import circlet.pipelines.provider.api.ServiceCredentials
import circlet.pipelines.provider.local.LocalExecutionProviderStorage
import circlet.platform.api.nowMs
import libraries.io.random.Random
import libraries.klogging.KLogging

class CircletIdeaExecutionProviderStorage : LocalExecutionProviderStorage {
    companion object : KLogging()

    private val idStorage = TaskLongIdStorage()

    private val stepExecutions = mutableListOf<AStepExecutionEntity<*>>()

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

    override fun registerSnapshot(snapshotId: String, stepExecutionId: StepExecId) {
        val step = findStepExecution(stepExecutionId, false) ?: error("Execution step is not found")
        volumeSnapshots.add(CircletIdeaVolumeSnapshotEntity(Random.nextLong(), snapshotId, nowMs, step.graph.id, stepExecutionId.value, this))
    }

    override fun deleteSnapshotsByGraphExecution(graphExecution: AGraphExecutionEntity): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findRunningJobsWithWorkerId(limit: Int): Sequence<AStepExecutionEntity<*>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findStepExecution(id: StepExecId, forUpdate: Boolean): AStepExecutionEntity<ScriptStep.Process<*, *>>? {
        return stepExecutions.firstOrNull { it.id == id.value }
    }

    override fun findStepExecutions(ids: List<StepExecId>, forUpdate: Boolean): Sequence<AStepExecutionEntity<ScriptStep.Process<*, *>>> {
        val idValues = ids.map { it.value }
        return stepExecutions.filter { it.id in idValues }.asSequence()
    }

    override fun findAuthClient(graphExecution: AGraphExecutionEntity): ServiceCredentials? {
        // empty creds here.
        return ServiceCredentials("", "")
    }

    override fun findSnapshotForStepExecution(stepExec: AStepExecutionEntity<*>): AVolumeSnapshotEntity? {
        return volumeSnapshots.firstOrNull { it.stepExecutionId == stepExec.id }
    }

    override fun findSnapshotCreationPoint(graphExecutionId: GraphExecId): AStepExecutionEntity<ScriptStep.Process<*, *>>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun findGraphExecutionById(graphExecutionId: GraphExecId): AGraphExecutionEntity? {
        return executions.firstOrNull { it.id == graphExecutionId.value }
    }

    override fun createGraphExecution(

        actionMeta: ScriptAction,
        bootstrapStepFactory: (AGraphExecutionEntity) -> ScriptStep.Process.Container?): AGraphExecutionEntity {

        logger.debug { "createJobExecution $actionMeta" }

        val now = System.currentTimeMillis()
        val jobs = mutableListOf<AStepExecutionEntity<*>>()


        val graphExecutionEntity = CircletIdeaAGraphExecutionEntity(
            Random.nextLong(),
            now,
            null,
            ExecutionStatus.PENDING,
            actionMeta,
            null,
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

    override fun createStepExecutionCustomMessage(stepExec: StepExecId, message: String, severity: TextMessageSeverity) {
        TODO("Not yet implemented")
    }

    override suspend fun <T> invoke(name: String, body: AutomationStorageTransaction.() -> T): T {
        val tx = Transaction()
        val result = tx.body()
        tx.executeAfterTransaction()

        return result
    }

    private fun createAStepExecutionEntity(bootstrapJob: ScriptStep.Process.Container, graphExecution: AGraphExecutionEntity): AStepExecutionEntity<*> {
        val jobExecId = idStorage.getOrCreateId(bootstrapJob.id)
        val entity = CircletIdeaContainerStepExecutionEntity(
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
        stepExecutions.add(entity)
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

        override fun enableDebug() { }
    }
}
