package circlet.plugins.pipelines.services.execution

import circlet.pipelines.engine.*
import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.api.utils.*
import circlet.pipelines.provider.*
import circlet.pipelines.provider.api.*
import circlet.pipelines.provider.local.*
import circlet.pipelines.provider.local.docker.*
import libraries.coroutines.extra.*
import kotlin.coroutines.*

class CircletIdeaStepExecutionProvider(
    lifetime: Lifetime,
    vp: IdeaLocalVolumeProvider,
    db: LocalExecutionProviderStorage,
    docker: DockerFacade,
    reporting: LocalReporting,
    dockerEventsProcessContext: CoroutineContext,
    tracer: AutomationTracer,
    failureChecker: FailureChecker,
    statusHub: StepExecutionStatusHub
) : LocalStepExecutionProviderImpl(
    lifetime,
    db,
    vp,
    docker,
    reporting,
    dockerEventsProcessContext,
    tracer,
    failureChecker,
    statusHub
) {

    override fun resolveContainerContext(jobExec: ContainerExecutionData, volumeName: String?, clientCredentials: ServiceCredentials?): StartContainerContext {
        return StartContainerContext(
            jobExec.graph.id,
            jobExec.id,
            jobExec.graph.executionNumber,
            jobExec.graph.projectKey,
            jobExec.graph.projectId,
            jobExec.graph.commit,
            jobExec.graph.branch,
            "idea",
            "idea",
            jobExec.volumeSize,
            clientCredentials?.clientId,
            clientCredentials?.clientSecret,
            jobExec.services,
            jobExec.graph.executionMeta.steps.toFlowGraph().predecessorSteps(jobExec.meta.id).map { it.id }
        )

    }


}


