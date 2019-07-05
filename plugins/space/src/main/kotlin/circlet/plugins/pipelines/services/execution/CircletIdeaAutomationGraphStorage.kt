package circlet.plugins.pipelines.services.execution

import circlet.pipelines.config.api.*
import circlet.pipelines.engine.api.storage.*

class CircletIdeaAutomationGraphStorage(internal val task: ProjectTask) : AutomationGraphStorage {
    internal val idStorage = TaskLongIdStorage()
    internal val storedExecutions = mutableMapOf<Long, AJobExecutionEntity<*>>()

    val singletonTx = CircletIdeaGraphStorageTransaction(this)

    override suspend fun <T> invoke(body: (GraphStorageTransaction) -> T): T {
        val res = body(singletonTx)
        singletonTx.executeAfterTransactionHooks()
        return res
    }
}
