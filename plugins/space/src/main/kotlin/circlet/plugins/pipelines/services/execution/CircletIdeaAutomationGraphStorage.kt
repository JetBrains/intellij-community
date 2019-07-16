package circlet.plugins.pipelines.services.execution

import circlet.pipelines.config.api.*
import circlet.pipelines.engine.api.storage.*

class CircletIdeaAutomationGraphStorage(internal val task: ProjectTask) : AutomationGraphStorage<GraphStorageTransaction> {
    internal val idStorage = TaskLongIdStorage()
    internal val storedExecutions = mutableMapOf<Long, AJobExecutionEntity<*>>()

    override suspend fun <T> invoke(body: (GraphStorageTransaction) -> T): T {
        val tx = CircletIdeaGraphStorageTransaction(this)
        val res = body(tx)
        tx.executeAfterTransactionHooks()
        return res
    }

}
