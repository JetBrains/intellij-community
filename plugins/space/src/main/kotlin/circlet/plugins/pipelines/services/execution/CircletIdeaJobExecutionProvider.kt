package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.api.storage.*
import libraries.klogging.*
import runtime.*
import runtime.reactive.*

data class DummyContainer(val lifetimeSource: LifetimeSource)

class CircletIdeaJobExecutionProvider(
    private val lifetime: Lifetime,
    private val logCallback: (String) -> Unit,
    private val notifyProcessTerminated: (Int) -> Unit,
    private val tx: CircletIdeaGraphStorageTransaction
) : JobExecutionProvider, JobExecutionScheduler {

    companion object : KLogging()

    private val runningJobs = mutableMapOf<Long, DummyContainer>()

    private lateinit var savedHandler: (tx: GraphStorageTransaction, job: AJobExecutionEntity<*>, newStatus: ExecutionStatus) -> Unit

    override fun scheduleExecution(jobExecs: Iterable<JobExecutionData<*>>) = launch { startExecution(jobExecs) }

    override fun scheduleTermination(jobExecs: Iterable<JobExecutionData<*>>) = launch { startTermination(jobExecs) }

    override suspend fun startExecution(jobExecs: Iterable<JobExecutionData<*>>) {
        jobExecs.forEach { jobData ->
            val jobEntity = tx.findJobExecution(jobData.id) ?: error("Job execution [$jobData] is not found")
            if (jobEntity !is CircletIdeaAJobExecutionEntity) {
                error("unknown job $jobEntity")
            }

            val image = jobEntity.meta.image
            logCallback("prepare to run: image=$image, id=$jobData")
            val jobLifetimeSource = lifetime.nested()

            val dummyContainer = DummyContainer(jobLifetimeSource)
            runningJobs[jobData.id] = dummyContainer
            changeState(tx, jobEntity, ExecutionStatus.RUNNING)

            var counter = 0

            val timer = UiDispatch.dispatchInterval(1000) {
                logCallback("run dummy container '$image'. counter = ${counter++}")
                if (counter == 3) {
                    jobLifetimeSource.terminate()
                }
            }

            jobLifetimeSource.add {
                runningJobs.remove(jobData.id)?.lifetimeSource?.terminate()
                timer.cancel()
                logCallback("stop: image=$image, id=$jobData")

                changeState(tx, jobEntity, generateFinalState(image))
            }
        }
    }

    override suspend fun startTermination(jobExecs: Iterable<JobExecutionData<*>>) {
        TODO("startTermination not implemented")
    }

    override fun subscribeIdempotently(handler: (tx: GraphStorageTransaction, job: AJobExecutionEntity<*>, newStatus: ExecutionStatus) -> Unit) {
        this.savedHandler = handler
    }

    override fun onBeforeGraphStatusChanged(tx: GraphStorageTransaction, entity: AGraphExecutionEntity, oldStatus: ExecutionStatus, newStatus: ExecutionStatus) {
        if (newStatus.isFinished()) {
            notifyProcessTerminated(0)
        }
    }

    override fun onBeforeJobStatusChanged(tx: GraphStorageTransaction, entity: AJobExecutionEntity<*>, oldStatus: ExecutionStatus, newStatus: ExecutionStatus) {
        //todo
    }

    private fun changeState(tx: GraphStorageTransaction, job: AJobExecutionEntity<*>, newStatus: ExecutionStatus) {
        savedHandler(tx, job, newStatus)
    }

    private fun generateFinalState(imageName: String) : ExecutionStatus {
        if (imageName.endsWith("_toFail")) {
            return ExecutionStatus.FAILED
        }
        return ExecutionStatus.SUCCEEDED
    }

    private fun launch(body: suspend () -> Unit) {
        runtime.async.launch(lifetime, Ui) {
            body()
        }
    }
}
