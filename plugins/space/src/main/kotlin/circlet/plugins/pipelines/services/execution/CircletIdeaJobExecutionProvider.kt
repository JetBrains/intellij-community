package circlet.plugins.pipelines.services.execution

import circlet.pipelines.engine.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.storage.*
import libraries.klogging.*

class CircletIdeaJobExecutionProvider : JobExecutionProvider<CircletIdeaGraphStorageTransaction> {

    companion object : KLogging()

    private var savedHandler: ((tx: CircletIdeaGraphStorageTransaction, job: AJobExecutionEntity<*>, newStatus: ExecutionStatus) -> Unit)? = null

    override fun scheduleExecution(tx: CircletIdeaGraphStorageTransaction, jobs: Iterable<AJobExecutionEntity<*>>) {
        jobs.forEach { job ->
            when (job) {
                is CircletIdeaAJobExecutionEntity -> {
                    val image = job.meta.image
                }
                else -> error("unknown job $job")
            }
        }
        TODO("scheduleExecution not implemented ${jobs.joinToString()}")
    }

    override fun scheduleTermination(jobs: Iterable<AJobExecutionEntity<*>>) {
        TODO("scheduleTermination not implemented")
    }

    override fun subscribeIdempotently(handler: (tx: CircletIdeaGraphStorageTransaction, job: AJobExecutionEntity<*>, newStatus: ExecutionStatus) -> Unit) {
        if (savedHandler != null) {
            logger.warn { "subscribeIdempotently. savedHandler != null" }
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
