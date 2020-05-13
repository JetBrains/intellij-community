package circlet.plugins.pipelines.services.execution

import circlet.pipelines.engine.api.storage.*

data class CircletIdeaVolumeSnapshotEntity(
    override val id: Long,
    override val snapshotId: String,
    override val createdTime: Long,
    val grapthExecutionId : Long,
    val stepExecutionId : Long,
    private val storage: CircletIdeaExecutionProviderStorage
) : AVolumeSnapshotEntity {
    override val graph: AGraphExecutionEntity get() = storage.findGraphExecutionById(grapthExecutionId) ?: error("Execution is not found")
}
