package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.api.utils.*
import com.intellij.execution.process.*

class CircletIdeaAutomationTracer(private val processHandler: TaskProcessHandler) : AutomationTracer {

    override fun traceCommit(commit: CommitHash, message: String, level: TraceLevel) {
        processHandler.message("Commit $commit [$level]: $message", level)
    }

    override fun traceStep(executionId: Long, message: String, level: TraceLevel) {
        processHandler.message("Step execution $executionId [$level]: $message", level)
    }

    override fun traceGraph(executionId: Long, message: String, level: TraceLevel) {
        processHandler.message("Graph execution $executionId [$level]: $message", level)
    }

}

private val newLine: String = System.getProperty("line.separator", "\n")


fun ProcessHandler.message(text: String, level: TraceLevel) {
    val key = when (level) {
        TraceLevel.TRACE -> ProcessOutputTypes.STDOUT
        TraceLevel.INFO -> ProcessOutputTypes.STDOUT
        TraceLevel.WARN -> ProcessOutputTypes.STDOUT
        TraceLevel.ERROR -> ProcessOutputTypes.STDERR
    }

    this.notifyTextAvailable("$text$newLine", key)
}
