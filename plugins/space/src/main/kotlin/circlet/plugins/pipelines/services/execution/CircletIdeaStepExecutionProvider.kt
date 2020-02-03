package circlet.plugins.pipelines.services.execution

import circlet.pipelines.engine.api.*
import circlet.pipelines.engine.api.utils.*
import circlet.pipelines.provider.*
import circlet.pipelines.provider.local.*
import circlet.pipelines.provider.local.docker.*
import libraries.coroutines.extra.*
import kotlin.coroutines.*

class CircletIdeaStepExecutionProvider(
    lifetime: Lifetime,
    db: LocalExecutionProviderStorage,
    volumeProvider: VolumeProvider,
    docker: DockerFacade,
    reporting: LocalReporting,
    dockerEventsProcessContext: CoroutineContext,
    tracer: AutomationTracer,
    failureChecker: FailureChecker,
    statusHub: StepExecutionStatusHub
) : StepExecutionProvider {

    val local = LocalStepExecutionProviderImpl(lifetime, db, volumeProvider, docker, reporting, dockerEventsProcessContext, tracer, failureChecker, statusHub)

    override suspend fun startExecution(stepExec: StepExecutionData<*>) {
        local.startExecution(stepExec)
    }

}


