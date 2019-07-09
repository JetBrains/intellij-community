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
    val jobsList: MutableList<AJobExecutionEntity<*>>
) : AGraphExecutionEntity {

    override val jobs: Sequence<AJobExecutionEntity<*>>
        get() = jobsList.asSequence()
}
