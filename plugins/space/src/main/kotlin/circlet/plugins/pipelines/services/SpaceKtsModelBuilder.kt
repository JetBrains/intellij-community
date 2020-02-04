package circlet.plugins.pipelines.services

import circlet.pipelines.config.api.*
import circlet.pipelines.config.dsl.compile.*
import circlet.pipelines.config.dsl.resolve.*
import circlet.pipelines.config.dsl.script.exec.common.*
import circlet.pipelines.config.utils.*
import circlet.plugins.pipelines.utils.*
import circlet.plugins.pipelines.viewmodel.*
import circlet.utils.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.*
import com.intellij.openapi.vfs.*
import libraries.coroutines.extra.*
import libraries.klogging.*
import org.slf4j.event.*
import org.slf4j.helpers.*
import runtime.reactive.*
import java.io.*

@Service
class SpaceKtsModelBuilder(val project: Project) : LifetimedDisposable by LifetimedDisposableImpl(), KLogging() {

    private val _model = mutableProperty<ScriptModelHolder?>(null)

    private val _modelBuildIsRequested = mutableProperty(false)

    // script is null when no build file is found in project
    val script = map(_model) { it as ScriptModel? }

    fun requestModel() {
        _modelBuildIsRequested.value = true
    }

    fun rebuildModel() {
        _model.value?.rebuildModel()
    }

    init {
        project.service<SpaceKtsFileDetector>().dslFile.view(lifetime) { lt, file ->
            _model.value = file?.let { ScriptModelHolder(lt, file, _modelBuildIsRequested) }
        }
    }

    // this class is created per automation script file found and it is responsible for building model content
    private inner class ScriptModelHolder(val lifetime: Lifetime, val scriptFile: VirtualFile, modelBuildIsRequested: Property<Boolean>) : ScriptModel {

        val sync = Any()

        private val _config = mutableProperty<ScriptConfig?>(null)
        private val _error = mutableProperty<String?>(null)
        private val _state = mutableProperty(ScriptState.NotInitialised)

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
                    val prefix = if (ev.level == Level.ERROR) "Error: " else ""
                    val resMessage = "$prefix${ev.message}"
                    logData.add(resMessage)
                    logger.debug(resMessage)
                }

                val eventLogger = KLogger(
                    JVMLogger(
                        SubstituteLogger("ScriptModelBuilderLogger", events, false)
                    )
                )

                try {
                    val p = PathManager.getSystemPath() + "/.kotlinc/"
                    val path = normalizePath(p)

                    val kotlinCompilerPath = KotlinCompilerFinder(eventLogger)
                        .find(if (path.endsWith('/')) path else "$path/")
                    logger.debug("build. path to kotlinc: $kotlinCompilerPath")

                    val scriptDefFile = JarFinder.find("pipelines-config-dsl-scriptdefinition")
                    logger.debug("build. path to `pipelines-config-dsl-scriptdefinition` jar: $scriptDefFile")

                    val outputFolder = createTempDir().absolutePath + "/"
                    val targetJar = outputFolder + "compiledJar.jar"
                    val resolveResultPath = outputFolder + "compilationResolveResult"

                    DslJarCompiler(eventLogger).compile(
                        DslSourceFileDelegatingFileProvider(scriptFile.path),
                        targetJar,
                        resolveResultPath,
                        kotlinCompilerPath,
                        scriptDefFile.absolutePath,
                        allowNotReadyDsl = false)

                    val scriptResolveResult = ScriptResolveResult.readFromFileOrEmpty(resolveResultPath)
                    val config = DslScriptExecutor().evaluateModel(targetJar, scriptResolveResult.classpath)

                    _error.value = null
                    _config.value = config
                } catch (e: Exception) {
                    val errors = StringWriter()
                    e.printStackTrace(PrintWriter(errors))
                    logger.error("${e.message}. $errors")

                    // do not touch last config, just update the error state.
                    _error.value = errors.toString()
                }

            }

        }

    }


}
