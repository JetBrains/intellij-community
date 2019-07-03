package circlet.plugins.pipelines.services.execution

import circlet.pipelines.config.api.*
import circlet.pipelines.engine.*
import circlet.pipelines.engine.storage.*

class CircletIdeaAutomationBootstrapper : AutomationBootstrapper {
    override fun createBootstrapJob(execution: AGraphExecutionEntity, repository: RepositoryData, orgUrl: String): ProjectJob.Process.Container {
        val container = ProjectJob.Process.Container(
            "hello-world",
            ProjectJob.ProcessData(
                exec = ProjectJob.ProcessExecutable.ContainerExecutable.DefaultCommand(emptyList())
            )
        )
        container.applyIds()
        return container
    }
}
