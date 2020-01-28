package circlet.plugins.pipelines.services.execution

import circlet.pipelines.engine.api.storage.*

class CircletIdeaAGraphExecutionContextEntity(
    override val branch: String,
    override val commit: String,
    override val repoId: String,
    override val projectKey: String,
    override val projectId: String,
    override val executionNumber: Long
): AGraphExecutionContext
