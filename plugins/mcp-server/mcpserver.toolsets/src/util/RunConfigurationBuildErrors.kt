package com.intellij.mcpserver.toolsets.util

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildProgressListenerRegistrar
import com.intellij.build.BuildProgressObservable
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.Failure
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.FinishEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.annotations.ApiStatus
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@ApiStatus.Internal
class RunConfigurationBuildErrors(private val executionId: () -> Long) : BuildProgressListener {
  private val buildIds = ConcurrentHashMap.newKeySet<Any>()
  private val errors = ConcurrentLinkedQueue<String>()
  private val processOutputs = ConcurrentHashMap<ProcessHandler, StringBuilder>()

  fun listen(project: Project, disposable: Disposable) {
    project.service<RunConfigurationBuildEvents>().addListener(this, disposable)
    project.messageBus.connect(disposable).subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
      override fun processStarting(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        val id = executionId()
        if (id == 0L || env.executionId != id) return
        val output = StringBuilder()
        processOutputs[handler] = output
        val listener = object : ProcessListener {
          override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            if (outputType === ProcessOutputType.SYSTEM) return
            val text = event.text
            synchronized(output) {
              val start = maxOf(0, text.length - MAX_PROCESS_OUTPUT_LENGTH)
              output.append(text, start, text.length)
              output.delete(0, maxOf(0, output.length - MAX_PROCESS_OUTPUT_LENGTH))
            }
          }
        }
        handler.addProcessListener(listener)
        Disposer.register(disposable, Disposable { handler.removeProcessListener(listener) })
      }
    })
  }

  override fun onEvent(buildId: Any, event: BuildEvent) {
    if (event is StartBuildEvent) {
      val descriptor = event.buildDescriptor
      val id = executionId()
      if (id != 0L && (descriptor.id == id || (descriptor as? DefaultBuildDescriptor)?.executionEnvironment?.executionId == id)) {
        buildIds.add(buildId)
      }
    }
    if (buildId !in buildIds) return

    if (event is MessageEvent && event.kind == MessageEvent.Kind.ERROR) {
      val message = event.result.details?.takeIf { it.isNotBlank() } ?: event.message
      val position = (event as? FileMessageEvent)?.filePosition
      val path = position?.path
      errors.add(if (path != null) "${path}:${position.startLine + 1}: $message" else message)
    }
    if (event is FinishEvent) {
      val result = event.result
      if (result is FailureResult) {
        result.failures.forEach(::addFailure)
      }
    }
  }

  private fun addFailure(failure: Failure) {
    val message = failure.description?.takeIf { it.isNotBlank() } ?: failure.message
    if (!message.isNullOrBlank()) errors.add(message)
    failure.error?.let { errors.add(runConfigurationFailureText(it)) }
    failure.causes.forEach(::addFailure)
  }

  fun getErrorText(): String? {
    val buildErrors = errors.distinct().joinToString("\n")
    if (buildErrors.isNotBlank()) return buildErrors
    return processOutputs.entries
      .asSequence()
      .filter { (handler, _) -> handler.exitCode?.let { it != 0 } == true }
      .map { (_, output) -> synchronized(output) { output.toString() }.trim() }
      .filter { it.isNotBlank() }
      .distinct()
      .joinToString("\n")
      .takeIf { it.isNotBlank() }
  }

  private companion object {
    const val MAX_PROCESS_OUTPUT_LENGTH = 20_000
  }
}

/**
 * Connects each [BuildProgressObservable] of the project to [RunConfigurationBuildEvents].
 * The build view and the external system run configuration view are such observables.
 */
internal class RunConfigurationBuildListenerRegistrar : BuildProgressListenerRegistrar {
  override fun register(project: Project, buildProgressObservable: BuildProgressObservable) {
    project.service<RunConfigurationBuildEvents>().listenTo(buildProgressObservable)
  }
}

/**
 * Sends the build events of all connected observables to the listeners of the active run configuration starts.
 * A listener stays until its disposable is disposed.
 */
@Service(Service.Level.PROJECT)
internal class RunConfigurationBuildEvents : BuildProgressListener, Disposable {
  private val listeners = DisposableWrapperList<BuildProgressListener>()

  fun listenTo(buildProgressObservable: BuildProgressObservable) {
    buildProgressObservable.addListener(this, this)
  }

  fun addListener(listener: BuildProgressListener, disposable: Disposable) {
    listeners.add(listener, disposable)
  }

  override fun onEvent(buildId: Any, event: BuildEvent) {
    for (listener in listeners) {
      try {
        listener.onEvent(buildId, event)
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        LOG.warn(e)
      }
    }
  }

  override fun dispose() {
  }
}

private val LOG = logger<RunConfigurationBuildEvents>()

@ApiStatus.Internal
fun runConfigurationFailureText(error: Throwable): String {
  val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
  return generateSequence(error) { it.cause }
    .takeWhile(visited::add)
    .mapNotNull { it.message?.takeIf(String::isNotBlank) }
    .distinct()
    .joinToString("\n")
    .ifBlank { error.javaClass.name }
}
