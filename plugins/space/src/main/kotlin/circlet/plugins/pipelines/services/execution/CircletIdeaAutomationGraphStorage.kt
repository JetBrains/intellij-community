package circlet.plugins.pipelines.services.execution

import circlet.pipelines.config.api.*
import circlet.pipelines.engine.api.storage.*

class CircletIdeaAutomationGraphStorage(internal val task: ProjectTask) : AutomationGraphStorage<CircletIdeaGraphStorageTransaction> {
    internal val idStorage = TaskLongIdStorage()
    internal val storedExecutions = mutableMapOf<Long, AJobExecutionEntity<*>>()

    override suspend fun <T> invoke(txName: String?, body: (CircletIdeaGraphStorageTransaction) -> T): T {
        val tx = CircletIdeaGraphStorageTransaction(this)
        val res = body(tx)
        tx.executeAfterTransactionHooks()
        return res
    }

}
