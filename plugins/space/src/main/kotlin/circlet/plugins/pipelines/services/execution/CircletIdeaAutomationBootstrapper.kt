package circlet.plugins.pipelines.services.execution

import circlet.pipelines.config.api.*
import circlet.pipelines.engine.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.api.storage.*

class CircletIdeaAutomationBootstrapper : AutomationBootstrapper {
    override fun createBootstrapStep(execution: AGraphExecutionEntity, projectId: Long, repository: RepositoryData, orgUrl: String): ScriptStep.Process.Container {
        return ScriptStep.Process.Container(
            "imageForBootstrapJob",
            ScriptStep.ProcessData(
                exec = ScriptStep.ProcessExecutable.ContainerExecutable.DefaultCommand(emptyList())
            )
        )
    }
}
