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
    private val storage: CircletIdeaAutomationGraphStorage
) : JobExecutionProvider<CircletIdeaGraphStorageTransaction>, JobExecutionScheduler {

    companion object : KLogging()

    private val runningJobs = mutableMapOf<Long, DummyContainer>()

    private lateinit var savedHandler: JobExecutionStatusUpdateHandler<CircletIdeaGraphStorageTransaction>

    override fun scheduleExecution(jobExecs: Iterable<JobExecutionData<*>>) {
        jobExecs.forEach {
            logger.catch {
                launch { startExecution(it) }
            }
        }
    }

    override fun scheduleTermination(jobExecs: Iterable<JobExecutionData<*>>) {
        jobExecs.forEach {
            logger.catch {
                launch { startTermination(it) }
            }
        }
    }

    override suspend fun startExecution(jobExec: JobExecutionData<*>) = storage { tx ->
        val jobEntity = tx.findJobExecution(jobExec.id) ?: error("Job execution [$jobExec] is not found")
        if (jobEntity !is CircletIdeaAJobExecutionEntity) {
            error("unknown job $jobEntity")
        }

        val image = jobEntity.meta.image
        logCallback("prepare to run: image=$image, id=$jobExec")
        val jobLifetimeSource = lifetime.nested()

        val dummyContainer = DummyContainer(jobLifetimeSource)
        runningJobs[jobExec.id] = dummyContainer
        changeState(tx, jobEntity, ExecutionStatus.RUNNING)

        var counter = 0

        val timer = UiDispatch.dispatchInterval(1000) {
            logCallback("run dummy container '$image'. counter = ${counter++}")
            if (counter == 3) {
                jobLifetimeSource.terminate()
            }
        }

        jobLifetimeSource.add {
            runningJobs.remove(jobExec.id)?.lifetimeSource?.terminate()
            timer.cancel()
            logCallback("stop: image=$image, id=$jobExec")

            changeState(tx, jobEntity, generateFinalState(image))
        }
    }

    override suspend fun startTermination(jobExec: JobExecutionData<*>) {
        TODO("startTermination not implemented")
    }

    override fun subscribeIdempotently(handler: JobExecutionStatusUpdateHandler<CircletIdeaGraphStorageTransaction>) {
        this.savedHandler = handler
    }

    override fun onBeforeGraphStatusChanged(tx: CircletIdeaGraphStorageTransaction, entity: AGraphExecutionEntity, oldStatus: ExecutionStatus, newStatus: ExecutionStatus) {
        if (newStatus.isFinished()) {
            notifyProcessTerminated(0)
        }
    }

    override fun onBeforeJobStatusChanged(tx: CircletIdeaGraphStorageTransaction, entity: AJobExecutionEntity<*>, oldStatus: ExecutionStatus, newStatus: ExecutionStatus) {
        //todo
    }

    private fun changeState(tx: CircletIdeaGraphStorageTransaction, job: AJobExecutionEntity<*>, newStatus: ExecutionStatus) {
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
