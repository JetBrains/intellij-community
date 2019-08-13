package circlet.plugins.pipelines.services.execution

import circlet.pipelines.config.api.*
import circlet.pipelines.engine.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.api.storage.*

class CircletIdeaAutomationBootstrapper : AutomationBootstrapper {
    override fun createBootstrapJob(execution: AGraphExecutionEntity, repository: RepositoryData, orgUrl: String): ProjectJob.Process.Container {
        val container = ProjectJob.Process.Container(
            "imageForBootstrapJob",
            ProjectJob.ProcessData(
                exec = ProjectJob.ProcessExecutable.ContainerExecutable.DefaultCommand(emptyList())
            )
        )
        return container
    }
}
