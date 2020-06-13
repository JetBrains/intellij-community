package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.GraphExecId
import circlet.pipelines.engine.api.storage.AGraphExecutionEntity
import circlet.pipelines.engine.api.storage.AVolumeSnapshotEntity

data class CircletIdeaVolumeSnapshotEntity(
    override val id: Long,
    override val snapshotId: String,
    override val createdTime: Long,
    val grapthExecutionId : Long,
    val stepExecutionId : Long,
    private val storage: CircletIdeaExecutionProviderStorage
) : AVolumeSnapshotEntity {
    override val graph: AGraphExecutionEntity get() = storage.findGraphExecutionById(GraphExecId(grapthExecutionId)) ?: error("Execution is not found")
}
