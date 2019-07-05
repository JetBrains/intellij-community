package circlet.plugins.pipelines.services.execution

import circlet.pipelines.config.api.*
import circlet.pipelines.engine.api.storage.*

class CircletIdeaAutomationGraphStorage(private val task: ProjectTask) : AutomationGraphStorage<CircletIdeaGraphStorageTransaction> {
    override suspend fun <T> invoke(body: (CircletIdeaGraphStorageTransaction) -> T): T {
        val tx = CircletIdeaGraphStorageTransaction(task)
        val res = body(tx)
        tx.executeAfterTransactionHooks()
        return res
    }
}
