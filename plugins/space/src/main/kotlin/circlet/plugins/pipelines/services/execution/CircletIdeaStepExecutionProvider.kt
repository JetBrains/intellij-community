package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.api.storage.*
import libraries.coroutines.extra.*
import libraries.klogging.*
import runtime.*

data class DummyContainer(val lifetimeSource: LifetimeSource)

class CircletIdeaStepExecutionProvider(
    private val lifetime: Lifetime,
    private val logCallback: (String) -> Unit,
    private val notifyProcessTerminated: (Int) -> Unit,
    private val db: CircletIdeaExecutionProviderStorage
) : StepExecutionProvider, StepExecutionScheduler {

    companion object : KLogging()

    private val runningJobs = mutableMapOf<Long, DummyContainer>()

    private lateinit var savedHandler: StepExecutionStatusUpdateHandler

    override fun scheduleExecution(stepExecs: Iterable<StepExecutionData<*>>) {
        stepExecs.forEach {
            logger.catch {
                launch { startExecution(it) }
            }
        }
    }

    override fun scheduleTermination(stepExecs: Iterable<StepExecutionData<*>>) {
        stepExecs.forEach {
            logger.catch {
                launch { startTermination(it) }
            }
        }
    }

    override suspend fun startExecution(stepExec: StepExecutionData<*>) = db("start-execution") {
        val jobEntity = db.findJobExecution(stepExec.id) ?: error("Job execution [$stepExec] is not found")
        if (jobEntity !is CircletIdeaAContainerStepExecutionEntity) {
            error("unknown job $jobEntity")
        }

        val image = jobEntity.meta.image
        logCallback("prepare to run: image=$image, id=$stepExec")
        val jobLifetimeSource = lifetime.nested()

        val dummyContainer = DummyContainer(jobLifetimeSource)
        runningJobs[stepExec.id] = dummyContainer
        changeState(this, jobEntity, StepState.Running)

        var counter = 0

        val timer = UiDispatch.dispatchInterval(1000) {
            logCallback("run dummy container '$image'. counter = ${counter++}")
            if (counter == 3) {
                jobLifetimeSource.terminate()
            }
        }

        jobLifetimeSource.add {
            runningJobs.remove(stepExec.id)
            timer.cancel()
            logCallback("stop: image=$image, id=$stepExec")
            lifetime.launch(Ui) {
                db("start-execution") {
                    changeState(this, jobEntity, generateFinalState(image))
                }
            }
        }
    }

    override suspend fun startTermination(stepExec: StepExecutionData<*>) {
        TODO("startTermination not implemented")
    }

    override fun subscribeIdempotently(handler: StepExecutionStatusUpdateHandler) {
        this.savedHandler = handler
    }

    override fun onBeforeGraphStatusChanged(tx: AutomationStorageTransaction, events: Iterable<GraphStatusChangedEvent>) {
        if (events.single().newStatus.isFinished()) {
            notifyProcessTerminated(0)
        }
    }

    override fun onBeforeJobStatusChanged(tx: AutomationStorageTransaction, events: Iterable<StepStatusChangedEvent>) {
        //todo
    }

    private fun changeState(tx: AutomationStorageTransaction, step: AStepExecutionEntity<*>, newStatus: StepState) {
        savedHandler(tx, setOf(StepExecutionStatusUpdate(step, newStatus)))
    }

    private fun generateFinalState(imageName: String) : StepState {
        if (imageName.endsWith("_toFail")) {
            return StepState.Failed("Should fail because of the image name $imageName")
        }
        return StepState.Finished(0)
    }

    private fun launch(body: suspend () -> Unit) {
        launch(lifetime, Ui) {
            body()
        }
    }
}
