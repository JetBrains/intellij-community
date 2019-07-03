package circlet.plugins.pipelines.services.execution

import circlet.pipelines.config.api.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.storage.*

class CircletIdeaAGraphExecutionEntity(
    override val id: Long,
    override var triggerTime: Long,
    override var startTime: Long?,
    override var endTime: Long?,
    override var status: ExecutionStatus,
    override val graph: AGraphMetaEntity,
    override var executionMeta: ProjectAction,
    val jobsList: MutableList<AJobExecutionEntity<*>>
) : AGraphExecutionEntity {

    override val jobs: Sequence<AJobExecutionEntity<*>>
        get() = jobsList.asSequence()
}
