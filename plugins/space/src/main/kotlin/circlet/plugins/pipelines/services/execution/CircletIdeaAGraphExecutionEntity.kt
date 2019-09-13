package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.*
import circlet.pipelines.config.api.*
import circlet.pipelines.engine.api.storage.*

class CircletIdeaAGraphExecutionEntity(
    override val id: Long,
    override var triggerTime: Long,
    override var startTime: Long?,
    override var endTime: Long?,
    override var status: ExecutionStatus,
    override val graphMeta: AGraphMetaEntity,
    override var executionMeta: ProjectAction,
    override val branch: String,
    override val commit: String,
    val jobsList: MutableList<AJobExecutionEntity<*>>
) : AGraphExecutionEntity {

    override val jobs: Sequence<AJobExecutionEntity<*>>
        get() = jobsList.asSequence()

    override fun equals(other: Any?): Boolean {
        if (other !is CircletIdeaAGraphExecutionEntity) return false
        return this.id == other.id
    }

    override fun hashCode(): Int {
        return this.id.hashCode()
    }

    override val problems = emptySet<Int>()
}
