package circlet.plugins.pipelines.services.execution

import circlet.pipelines.config.api.*
import circlet.pipelines.engine.api.storage.*

class CircletIdeaAGraphMetaEntity(
    override val id: Long,
    override val originalMeta: ProjectAction
) : AGraphMetaEntity
