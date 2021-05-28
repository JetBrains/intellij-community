// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.plugins.pipelines.services

import circlet.automation.bootstrap.AutomationCompilerBootstrap
import circlet.automation.bootstrap.AutomationDslEvaluationBootstrap
import circlet.automation.bootstrap.embeddedMavenServer
import circlet.automation.bootstrap.publicMavenServer
import circlet.pipelines.config.idea.api.IdeaScriptConfig
import circlet.pipelines.config.utils.AutomationCompilerConfiguration
import circlet.platform.client.backgroundDispatcher
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.plugins.pipelines.utils.ObservableQueue
import com.intellij.space.plugins.pipelines.viewmodel.LogData
import com.intellij.space.plugins.pipelines.viewmodel.ScriptModel
import com.intellij.space.plugins.pipelines.viewmodel.ScriptState
import com.intellij.space.utils.LifetimedDisposable
import com.intellij.space.utils.LifetimedDisposableImpl
import kotlinx.coroutines.withContext
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import libraries.coroutines.extra.using
import libraries.klogging.*
import org.slf4j.event.Level
import org.slf4j.event.SubstituteLoggingEvent
import org.slf4j.helpers.SubstituteLogger
import runtime.Ui
import runtime.reactive.*
import runtime.reactive.property.flatMap
import runtime.reactive.property.map
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.name

private val log = logger<SpaceKtsModelBuilder>()

@Service
class SpaceKtsModelBuilder(val project: Project) : LifetimedDisposable by LifetimedDisposableImpl(), KLogging() {
  private val persistentState = ScriptKtsPersistentState(project)

  private val _model = mutableProperty<ScriptModelHolder?>(null)
  private val _modelBuildIsRequested = mutableProperty(false)

  // script is null when no build file is found in project
  @Suppress("USELESS_CAST")
  val script = map(_model) { it as ScriptModel? }

  val config = flatMap(_model) { (it?.config ?: mutableProperty(null)) }

  init {
    launch(lifetime, backgroundDispatcher) {
      val saved = log.catch { persistentState.load() }
      withContext(Ui) {
        project.service<SpaceKtsFileDetector>().dslFile.view(lifetime) { lt, file ->
          _model.value = file?.let { ScriptModelHolder(lt, file, _modelBuildIsRequested, saved) }
        }
        config.skip(1).view(lifetime) { lt, config ->
          if (config != null) {
            launch(lt, backgroundDispatcher) {
              persistentState.save(config)
            }
          }
        }
      }
    }
  }


  fun requestModel() {
    _modelBuildIsRequested.value = true
  }

  fun rebuildModel() {
    _model.value?.rebuildModel()
  }

  // this class is created per automation script file found and it is responsible for building model content
  private inner class ScriptModelHolder(val lifetime: Lifetime,
                                        val scriptFile: VirtualFile,
                                        modelBuildIsRequested: Property<Boolean>,
                                        loadedConfig: IdeaScriptConfig?) : ScriptModel {

    val sync = Any()

    private val _config = mutableProperty(loadedConfig)
    private val _error = mutableProperty<String?>(null)
    private val _state = mutableProperty(if (loadedConfig == null) ScriptState.NotInitialised else ScriptState.Ready)

    override val config: Property<IdeaScriptConfig?> get() = _config
    override val error: Property<String?> get() = _error
    override val state: Property<ScriptState> get() = _state

    private val logBuildData = mutableProperty<LogData?>(null)

    init {

      // push to IDE build log
      logBuildData.view(lifetime) { lt, log ->
        if (log != null) {
          publishBuildLog(project, log)
        }
      }

      modelBuildIsRequested.whenTrue(lifetime) { lt ->
        if (_state.value == ScriptState.NotInitialised)
          rebuildModel()
      }

    }

    fun rebuildModel() {
      ProgressManager.getInstance().run(object : Task.Backgroundable(
        project,
        SpaceBundle.message("kts.progress.title.building.dsl.model"),
        false
      ) {
        override fun run(pi: ProgressIndicator) {
          // todo: implement build cancellation and re-building
          synchronized(sync) {
            if (_state.value == ScriptState.Building)
              return
            _state.value = ScriptState.Building
          }
          val data = LogData()
          try {
            logBuildData.value = data
            build(data)
          }
          finally {
            data.close()
            _state.value = ScriptState.Ready
          }
        }
      })
    }

    private fun build(logData: LogData) {
      lifetime.using { lt ->
        invokeAndWaitIfNeeded(ModalityState.NON_MODAL) {
          // We need to save the automation script to disk before compiling, to take into account the most recent changes.
          // The script might have dependencies on other files in the project, so we also save other files.
          FileDocumentManager.getInstance().saveAllDocuments()
        }
        compileAndEvaluate(logData.asLogger(lt), logData)
      }
    }

    private fun compileAndEvaluate(eventLogger: KLogger, logData: LogData) {
      val outputDir = createTempDir("space-automation-temp")
      try {
        val outputDirPath = outputDir.toPath()
        val compiledJarPath = outputDirPath.resolve("compiledJar.jar")
        val scriptRuntimePath = outputDirPath.resolve("space-automation-runtime.jar")

        val configuration = getAutomationConfigurationWithFallback()

        val compiler = AutomationCompilerBootstrap(log = eventLogger, configuration = configuration)
        val compileResultCode = compiler.compile(script = Paths.get(scriptFile.path), jar = compiledJarPath)

        if (compileResultCode != 0) {
          _config.value = null
          _error.value = SpaceBundle.message("kts.toolwindow.error.compilation.failed.code", compileResultCode)
          return
        }
        if (!Files.isRegularFile(compiledJarPath)) {
          _config.value = null
          _error.value = SpaceBundle.message("kts.toolwindow.error.missing.compiled.jar", compiledJarPath.name)
          return
        }
        // See AutomationCompiler.kt (in space project) where we copy the runtime jar into the output folder for all resolver types
        if (!Files.exists(scriptRuntimePath)) {
          _config.value = null
          _error.value = SpaceBundle.message("kts.toolwindow.error.missing.runtime.jar", scriptRuntimePath.name)
          return
        }

        val evaluator = AutomationDslEvaluationBootstrap(log = eventLogger, configuration = configuration).loadEvaluatorForIdea()
        if (evaluator == null) {
          _config.value = null
          _error.value = SpaceBundle.message("kts.toolwindow.error.evaluation.service.not.found")
          return
        }

        val evalResult = evaluator.evaluateAndValidate(compiledJarPath, scriptRuntimePath)
        if (evalResult.validationErrors.any()) {
          val validationErrorsPrefix = SpaceBundle.message("kts.toolwindow.validation.errors.prefix")
          val message = evalResult.validationErrors.joinToString("\n", prefix = "$validationErrorsPrefix\n")
          logData.error(message)
          _config.value = null
          _error.value = message
          return
        }

        _config.value = evalResult.config
        _error.value = null
      }
      catch (th: Throwable) {
        val stackTraceText = th.stackTraceToString()
        val logMessage = "${th.message}.\n$stackTraceText"
        logData.error(logMessage)
        // do not touch last config, just update the error state.
        _error.value = stackTraceText
      }
      finally {
        outputDir.deleteRecursively()
      }
    }
  }
}

internal fun getAutomationConfigurationOrNull(): AutomationCompilerConfiguration? =
  SpaceWorkspaceComponent.getInstance().workspace.value?.client?.server?.let {
    AutomationCompilerConfiguration.Remote(server = embeddedMavenServer(it))
  }


// Primary option is to download from currently connected server, fallback to the public maven
internal fun getAutomationConfigurationWithFallback(): AutomationCompilerConfiguration =
  getAutomationConfigurationOrNull() ?: AutomationCompilerConfiguration.Remote(server = publicMavenServer)

private fun LogData.asLogger(lifetime: Lifetime): KLogger {
  val queue = ObservableQueue.mutable<SubstituteLoggingEvent>()

  queue.change.forEach(lifetime) {
    val ev = it.index
    when (ev.level) {
      Level.INFO -> message(ev.message)
      Level.DEBUG -> message(ev.message)
      Level.WARN -> message(ev.message)
      Level.ERROR -> error(ev.message)
      Level.TRACE -> {
      }
    }
  }
  // slf4j's SubstituteLogger mechanism allows us to put logs as events in a queue (even though not initially made for this)
  return KLogger(JVMLogger(SubstituteLogger("ScriptModelBuilderLogger", queue, false)))
}
