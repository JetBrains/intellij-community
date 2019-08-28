package circlet.plugins.pipelines.services

import circlet.pipelines.*
import circlet.pipelines.config.api.*
import circlet.pipelines.config.dsl.compile.*
import circlet.pipelines.config.dsl.resolve.*
import circlet.pipelines.config.dsl.script.exec.common.*
import circlet.pipelines.config.utils.*
import circlet.plugins.pipelines.utils.*
import circlet.plugins.pipelines.viewmodel.*
import com.intellij.openapi.application.*
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.*
import libraries.klogging.*
import org.slf4j.event.*
import org.slf4j.helpers.*
import runtime.reactive.*
import java.io.*


object ScriptModelBuilder : KLogging() {
    fun updateModel(project: Project, viewModel: ScriptWindowViewModel) {
        logger.debug("update model requested")
        if (viewModel.modelBuildIsRunning.value) {
            logger.debug("modelBuildIsRunning == true")
            return
        }

        logger.debug("queue task. begin")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Build DSL Model", false) {
            override fun run(indicator: ProgressIndicator) {
                logger.debug("run task. begin")
                if (viewModel.modelBuildIsRunning.value) {
                    logger.debug("modelBuildIsRunning == true inside tak")
                    return
                }
                viewModel.modelBuildIsRunning.value = true
                val lt = viewModel.scriptLifetimes.next()
                val logBuildData = LogData("")
                viewModel.logBuildData.value = logBuildData
                val model = build(lt, project, logBuildData)
                viewModel.script.value = model
                logger.debug("run task. end $model")
            }
        })

        logger.debug("queue task. end")
    }

    private fun build(lifetime: Lifetime, project: Project, logBuildData: LogData): ScriptViewModel {
        val events = ObservableQueue.mutable<SubstituteLoggingEvent>()
        events.change.forEach(lifetime) {
            val ev = it.index
            val prefix = if (ev.level == Level.ERROR) "Error: " else ""
            val resMessage = "$prefix${ev.message}"
            logBuildData.add(resMessage)
            logger.debug(resMessage)
        }
        val logger = SubstituteLogger("ScriptModelBuilderLogger", events, false)

        val dslFile = DslFileFinder.find(project)

        if (dslFile == null)
        {
            logger.info("Can't find `$DefaultDslFileName`")
            return createEmptyScriptViewModel(lifetime)
        }

        try
        {
            val p = PathManager.getSystemPath() + "/.kotlinc/"
            val path = normalizePath(p)

            val kotlinCompilerPath = KotlinCompilerFinder(logger)
                .find(if (path.endsWith('/')) path else "$path/")
            logger.debug("build. path to kotlinc: $kotlinCompilerPath")

            val scriptDefFile = JarFinder.find("pipelines-config-dsl-scriptdefinition")
            logger.debug("build. path to `pipelines-config-dsl-scriptdefinition` jar: $scriptDefFile")

            val outputFolder = createTempDir().absolutePath + "/"
            val targetJar = outputFolder + "compiledJar.jar"
            val metadataPath = outputFolder + "compilationMetadata"
            DslJarCompiler(logger).compile(
                DslSourceFileDelegatingFileProvider(dslFile.path),
                targetJar,
                metadataPath,
                kotlinCompilerPath,
                scriptDefFile.absolutePath)

            val metadata = ScriptResolveResultMetadataUtil.tryReadFromFile(metadataPath) ?: ScriptResolveResultMetadataUtil.empty()
            val config = DslScriptExecutor().evaluateModel(targetJar, metadata.classpath,"", "", "")

            return ScriptViewModelFactory.create(lifetime, config)
        }
        catch (e: Exception)
        {
            val errors = StringWriter()
            e.printStackTrace(PrintWriter(errors))
            logger.error("${e.message}. $errors")
            return createEmptyScriptViewModel(lifetime)
        }
    }
}
