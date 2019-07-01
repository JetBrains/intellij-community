package circlet.plugins.pipelines.services.execution

import circlet.pipelines.config.api.*
import circlet.pipelines.engine.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.storage.*
import circlet.pipelines.engine.utils.*
import circlet.pipelines.utils.*
import libraries.klogging.*

class CircletIdeaAutomationGraphStorage: AutomationGraphStorage {
    override suspend fun <T> invoke(body: (GraphStorageTransaction) -> T): T {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class CircletIdeaAutomationBootstrapper: AutomationBootstrapper {
    override fun createBootstrapJob(execution: AGraphExecutionEntity, repository: RepositoryData, orgUrl: String): ProjectJob.Process.Container {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class CircletIdeaAutomationTracer : AutomationTracer {

    companion object : KLogging()

    override fun trace(commit: CommitHash, message: String) {
        logger.debug { "$commit $message" }
    }

    override fun trace(executionId: Long, message: String) {
        logger.debug { "$executionId $message" }
    }
}

class CircletIdeaJobExecutionProvider : JobExecutionProvider {

    companion object : KLogging()

    private var savedHandler: ((tx: GraphStorageTransaction, job: AJobExecutionEntity, newStatus: ExecutionStatus) -> Unit)? = null

    override fun scheduleExecution(jobs: Iterable<AJobExecutionEntity>) {
        TODO("not implemented CircletIdeaJobExecutionProvider::scheduleExecution") //To change body of created functions use File | Settings | File Templates.
    }

    override fun scheduleTermination(jobs: Iterable<AJobExecutionEntity>) {
        TODO("not implemented CircletIdeaJobExecutionProvider::scheduleTermination") //To change body of created functions use File | Settings | File Templates.
    }

    override fun subscribeIdempotently(handler: (tx: GraphStorageTransaction, job: AJobExecutionEntity, newStatus: ExecutionStatus) -> Unit) {
        if (savedHandler != null) {
            logger.warn { "subscribeIdempotently. savedHandler != null"}
        }
        this.savedHandler = handler
    }

    override fun onBeforeGraphStatusChanged(tx: GraphStorageTransaction, entity: AGraphExecutionEntity, oldStatus: ExecutionStatus, newStatus: ExecutionStatus) {
        TODO("not implemented CircletIdeaJobExecutionProvider::onBeforeGraphStatusChanged") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBeforeJobStatusChanged(tx: GraphStorageTransaction, entity: AJobExecutionEntity, oldStatus: ExecutionStatus, newStatus: ExecutionStatus) {
        TODO("not implemented CircletIdeaJobExecutionProvider::onBeforeJobStatusChanged") //To change body of created functions use File | Settings | File Templates.
    }

}

class SystemTimeTicker : Ticker {
    override val now: Long get() = System.currentTimeMillis()
}
