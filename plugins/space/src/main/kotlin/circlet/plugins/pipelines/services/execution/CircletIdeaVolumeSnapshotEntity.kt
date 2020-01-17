package circlet.plugins.pipelines.services.execution

import circlet.pipelines.engine.api.storage.*

data class CircletIdeaVolumeSnapshotEntity(
    override val id: Long,
    override val snapshotId: String,
    override val graph: AGraphExecutionEntity,
    override val createdTime: Long
) : AVolumeSnapshotEntity {
}
