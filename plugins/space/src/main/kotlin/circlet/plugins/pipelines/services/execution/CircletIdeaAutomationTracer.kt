package circlet.plugins.pipelines.services.execution

import circlet.pipelines.engine.*
import circlet.pipelines.engine.api.utils.*
import circlet.pipelines.engine.utils.*
import libraries.klogging.*

class CircletIdeaAutomationTracer : AutomationTracer {

    companion object : KLogging()

    override fun trace(commit: CommitHash, message: String) {
        logger.debug { "$commit $message" }
    }

    override fun trace(executionId: Long, message: String) {
        logger.debug { "$executionId $message" }
    }
}
