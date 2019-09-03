package circlet.plugins.pipelines.services.execution

import circlet.pipelines.config.api.*
import circlet.pipelines.engine.api.storage.*

class CircletIdeaAGraphMetaEntity(
    override val id: Long,
    override val originalMeta: ProjectAction
) : AGraphMetaEntity {
    override fun equals(other: Any?): Boolean {
        return (other as? CircletIdeaAGraphMetaEntity)?.id == this.id
    }

    override fun hashCode(): Int {
        return this.id.hashCode()
    }
}
