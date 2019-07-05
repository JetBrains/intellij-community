package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.*
import circlet.pipelines.config.api.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.api.storage.*
import libraries.klogging.*

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
            jobs
        )

        metaTask.originalMeta.jobs.traverseJobs {
            jobs.add(
                CircletIdeaAJobExecutionEntity(
                    idStorage.getOrCreateId(it.id),
                    now,
                    null,
                    null,
                    ExecutionStatus.SCHEDULED,
                    graphExecutionEntity,
                    it,
                    JobStartContext()
                )
            )
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

    override fun batchCreateJobExecutions(graphExecution: AGraphExecutionEntity, bootstrapJob: ProjectJob.Process.Container) {
        if (graphExecution is CircletIdeaAGraphExecutionEntity) {
            graphExecution.jobsList.add(
                CircletIdeaAJobExecutionEntity(
                    idStorage.getOrCreateId(bootstrapJob.id),
                    System.currentTimeMillis(),
                    null,
                    null,
                    ExecutionStatus.SCHEDULED,
                    graphExecution,
                    bootstrapJob,
                    JobStartContext()
                )
            )

            graphExecution.executionMeta = graphExecution.graph.originalMeta.prependJobs(listOf(bootstrapJob))
        } else {
            error("Unknown $graphExecution")
        }
    }
}
