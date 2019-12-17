package circlet.plugins.pipelines.services.execution

import circlet.pipelines.engine.api.storage.*

class CircletIdeaAGraphExecutionContextEntity(
    override val branch: String,
    override val commit: String,
    override val repoId: String
): AGraphExecutionContext
