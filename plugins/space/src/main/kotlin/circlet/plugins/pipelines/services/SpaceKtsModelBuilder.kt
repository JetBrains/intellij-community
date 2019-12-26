package circlet.plugins.pipelines.services

import circlet.pipelines.*
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
import libraries.coroutines.extra.*
import libraries.klogging.*
import org.slf4j.event.*
import org.slf4j.helpers.*
import runtime.reactive.*
import java.io.*

@Service
class SpaceKtsModelBuilder(val project: Project) : LifetimedDisposable by LifetimedDisposableImpl(), KLogging() {

    private val sync = Any()
    private val _script = mutableProperty<ScriptViewModel?>(null)
    private val _modelBuildIsRunning = mutableProperty(false)
    private val logBuildData = mutableProperty<LogData?>(null)
    private val detector = project.service<SpaceKtsFileDetector>()

    val script: Property<ScriptViewModel?> = _script
    val modelBuildIsRunning: Property<Boolean> = _modelBuildIsRunning

    init {

        detector.dslFile.view(lifetime) { lt, file ->
            rebuildModel()
        }

        // push to IDE build log
        logBuildData.view(lifetime) { lt, log ->
            if (log != null) {
                publishBuildLog(lt, project, log)
            }
        }
    }

    fun rebuildModel() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Build DSL Model", false) {
            override fun run(indicator: ProgressIndicator) {

                // todo: implement build cancellation and re-building
                synchronized(sync) {
                    if (_modelBuildIsRunning.value)
                        return
                    _modelBuildIsRunning.value = true
                }

                try {
                    val data = LogData()
                    logBuildData.value = data
                    val model = build(project, data)
                    _script.value = model
                } finally {
                    _modelBuildIsRunning.value = false
                }

            }
        })
    }

    private fun build(project: Project, logBuildData: LogData): ScriptViewModel {
        lifetime.using { lt ->
            val events = ObservableQueue.mutable<SubstituteLoggingEvent>()

            events.change.forEach(lt) {
                val ev = it.index
                val prefix = if (ev.level == Level.ERROR) "Error: " else ""
                val resMessage = "$prefix${ev.message}"
                logBuildData.add(resMessage)
                logger.debug(resMessage)
            }

            val eventLogger = KLogger(
                JVMLogger(
                    SubstituteLogger("ScriptModelBuilderLogger", events, false)
                )
            )

            val dslFile = DslFileFinder.find(project)

            if (dslFile == null) {
                logger.info("Can't find `$DefaultDslFileName`")
                return createEmptyScriptViewModel()
            }

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
                    DslSourceFileDelegatingFileProvider(dslFile.path),
                    targetJar,
                    resolveResultPath,
                    kotlinCompilerPath,
                    scriptDefFile.absolutePath,
                    allowNotReadyDsl = false)

                val scriptResolveResult = ScriptResolveResult.readFromFileOrEmpty(resolveResultPath)
                val config = DslScriptExecutor().evaluateModel(targetJar, scriptResolveResult.classpath, "", "")

                return ScriptViewModelFactory.create(config)
            } catch (e: Exception) {
                val errors = StringWriter()
                e.printStackTrace(PrintWriter(errors))
                logger.error("${e.message}. $errors")
                return createEmptyScriptViewModel()
            }

        }

    }

}
