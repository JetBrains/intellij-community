package circlet.plugins.pipelines.services

import circlet.automation.bootstrap.AutomationCompilerBootstrap
import circlet.automation.bootstrap.embeddedMavenServer
import circlet.automation.bootstrap.publicMavenServer
import circlet.components.circletWorkspace
import circlet.pipelines.config.api.ScriptConfig
import circlet.pipelines.config.dsl.script.exec.common.evaluateModel
import circlet.pipelines.config.utils.AutomationCompilerConfiguration
import circlet.platform.client.backgroundDispatcher
import circlet.plugins.pipelines.utils.ObservableQueue
import circlet.plugins.pipelines.viewmodel.LogData
import circlet.plugins.pipelines.viewmodel.ScriptModel
import circlet.plugins.pipelines.viewmodel.ScriptState
import circlet.utils.LifetimedDisposable
import circlet.utils.LifetimedDisposableImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import libraries.coroutines.extra.using
import libraries.coroutines.extra.withContext
import libraries.klogging.*
import org.slf4j.event.Level
import org.slf4j.event.SubstituteLoggingEvent
import org.slf4j.helpers.SubstituteLogger
import runtime.Ui
import runtime.reactive.*
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Paths

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
            withContext(lifetime, Ui) {
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
    private inner class ScriptModelHolder(val lifetime: Lifetime, val scriptFile: VirtualFile, modelBuildIsRequested: Property<Boolean>, loadedConfig: ScriptConfig?) : ScriptModel {

        val sync = Any()

        private val _config = mutableProperty(loadedConfig)
        private val _error = mutableProperty<String?>(null)
        private val _state = mutableProperty(if (loadedConfig == null) ScriptState.NotInitialised else ScriptState.Ready)

        override val config: Property<ScriptConfig?> get() = _config
        override val error: Property<String?> get() = _error
        override val state: Property<ScriptState> get() = _state

        val logBuildData = mutableProperty<LogData?>(null)

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
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Build DSL Model", false) {
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
                    } finally {
                        data.close()
                        _state.value = ScriptState.Ready
                    }
                }
            })
        }

        private fun build(logData: LogData) {
            lifetime.using { lt ->
                val events = ObservableQueue.mutable<SubstituteLoggingEvent>()

                events.change.forEach(lt) {
                    val ev = it.index
                    when (ev.level) {
                        Level.INFO -> {
                            logData.message(ev.message)
                        }
                        Level.DEBUG -> {
                            logData.message(ev.message)
                        }
                        Level.WARN -> {
                            logData.message(ev.message)
                        }
                        Level.ERROR -> {
                            logData.error(ev.message)
                        }
                        Level.TRACE -> {
                        }
                    }
                }

                val eventLogger = KLogger(
                    JVMLogger(
                        SubstituteLogger("ScriptModelBuilderLogger", events, false)
                    )
                )

                try {
                    val outputFolder = createTempDir().absolutePath + "/"
                    val targetJar = outputFolder + "compiledJar.jar"

                    // Primary option is to download from currently connected server, fallback on the public maven
                    val server = circletWorkspace.workspace.value?.client?.server?.let { embeddedMavenServer(it) } ?: publicMavenServer

                    val configuration = AutomationCompilerConfiguration.Remote(server = server)

                    val compile = AutomationCompilerBootstrap(eventLogger, configuration = configuration).compile(
                        Paths.get(scriptFile.path),
                        Paths.get(targetJar)
                    )

                    if (compile == 0) {
                        // TODO: fix script runtime path
                        val config = evaluateModel(targetJar, "")
                        _error.value = null
                        //_config.value = config
                    } else {
                        _error.value = "Compilation failed, $compile"
                    }

                } catch (th: Throwable) {
                    val errors = StringWriter()
                    th.printStackTrace(PrintWriter(errors))
                    val errorText = "${th.message}.\n$errors"
                    logData.error(errorText)
                    // do not touch last config, just update the error state.
                    _error.value = errors.toString()
                }
            }
        }
    }
}
