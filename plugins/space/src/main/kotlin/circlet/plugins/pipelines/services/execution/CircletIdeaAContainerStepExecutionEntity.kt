package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.*
import circlet.pipelines.config.api.*
import circlet.pipelines.engine.api.storage.*

class CircletIdeaAContainerStepExecutionEntity(
    override val id: Long,
    override var triggerTime: Long,
    override var startTime: Long?,
    override var endTime: Long?,
    override var status: ExecutionStatus,
    override val graph: AGraphExecutionEntity,
    override val meta: ScriptStep.Process.Container,
    override val baseVolumeName: String?,
    override val isSnapshotPoint: Boolean,
    override val volumeSize: Int?,
    override var workerId: String? = null
) : AContainerStepExecutionEntity {
    override fun equals(other: Any?): Boolean {
        return (other as? CircletIdeaAContainerStepExecutionEntity)?.id == this.id
    }

    override fun hashCode(): Int {
        return this.id.hashCode()
    }
}
