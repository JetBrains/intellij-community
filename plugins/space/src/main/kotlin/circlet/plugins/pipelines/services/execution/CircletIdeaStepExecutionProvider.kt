package circlet.plugins.pipelines.services.execution

import circlet.pipelines.common.api.StepExecId
import circlet.pipelines.engine.api.ContainerExecutionData
import circlet.pipelines.engine.api.GraphLifecycleListener
import circlet.pipelines.engine.api.StepExecutionScheduler
import circlet.pipelines.engine.api.storage.AStepExecutionEntity
import circlet.pipelines.engine.api.storage.AutomationStorageTransaction
import circlet.pipelines.engine.api.utils.AutomationTracer
import circlet.pipelines.engine.toFlowGraph
import circlet.pipelines.messages.TextMessageSeverity
import circlet.pipelines.provider.FailureChecker
import circlet.pipelines.provider.FinishConditionsChecker
import circlet.pipelines.provider.StepExecutionCustomMessages
import circlet.pipelines.provider.api.ServiceCredentials
import circlet.pipelines.provider.api.StartContainerContext
import circlet.pipelines.provider.local.LocalExecutionProviderStorage
import circlet.pipelines.provider.local.LocalReporting
import circlet.pipelines.provider.local.LocalStepExecutionProviderImpl
import circlet.pipelines.provider.local.docker.DockerFacade
import libraries.coroutines.extra.Lifetime
import kotlin.coroutines.CoroutineContext

class CircletIdeaStepExecutionProvider(
  lifetime: Lifetime,
  vp: IdeaLocalVolumeProvider,
  db: LocalExecutionProviderStorage,
  docker: DockerFacade,
  reporting: LocalReporting,
  dockerEventsProcessContext: CoroutineContext,
  tracer: AutomationTracer,
  failureChecker: FailureChecker,
  statusHub: StepExecutionScheduler,
  listeners: List<GraphLifecycleListener>
) : LocalStepExecutionProviderImpl(
    lifetime,
    vp,
    docker,
    reporting,
    dockerEventsProcessContext,
    db,
    tracer,
    failureChecker,
    listeners,
    statusHub,
    object : FinishConditionsChecker {
      override fun markExecutionFinishedCondition(tx: AutomationStorageTransaction,
                                                  stepExecutionId: StepExecId,
                                                  exitCode: Int,
                                                  reason: String?) {
        TODO("Not yet implemented")
      }

      override fun markMessagesReceivedCondition(tx: AutomationStorageTransaction, stepExecutionId: StepExecId) {
        TODO("Not yet implemented")
      }

      override fun markSnapshotCreatedCondition(tx: AutomationStorageTransaction, stepExecutionId: StepExecId) {
        TODO("Not yet implemented")
      }

      override fun subscribeOnAllConditionsMarked(lifetime: Lifetime,
                                                  handler: (AutomationStorageTransaction, AStepExecutionEntity<*>, Int, String?) -> Unit) {
        TODO("Not yet implemented")
      }


    },
    object: StepExecutionCustomMessages {
        override fun addCustomMessages(tx: AutomationStorageTransaction,
                                   stepExec: StepExecId,
                                   messages: List<Pair<String, TextMessageSeverity>>) {
        TODO("Not yet implemented")
    }
}
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


