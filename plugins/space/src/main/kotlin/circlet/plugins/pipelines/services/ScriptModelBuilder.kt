package circlet.plugins.pipelines.services

import circlet.components.*
import circlet.pipelines.config.api.*
import circlet.pipelines.config.dsl.compile.*
import circlet.pipelines.config.dsl.script.exec.common.*
import circlet.pipelines.config.utils.*
import circlet.plugins.pipelines.utils.*
import circlet.plugins.pipelines.viewmodel.*
import circlet.utils.*
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.*
import com.intellij.openapi.vfs.*
import libraries.klogging.*
import org.slf4j.event.*
import org.slf4j.helpers.*
import runtime.reactive.*
import java.io.*


object ScriptModelBuilder : KLogging() {
    fun updateModel(project: Project, viewModel: ScriptWindowViewModel) {
        if (viewModel.modelBuildIsRunning.value) {
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Build DSL Model", false) {
            override fun run(indicator: ProgressIndicator) {
                if (viewModel.modelBuildIsRunning.value) {
                    return
                }
                viewModel.modelBuildIsRunning.value = true
                val lt = viewModel.scriptLifetimes.next()
                val logBuildData = LogData("")
                viewModel.logBuildData.value = logBuildData
                val model = build(lt, project, logBuildData)
                viewModel.script.value = model
            }
        })
    }

    private fun build(lifetime: Lifetime, project: Project, logBuildData: LogData): ScriptViewModel {

        val events = ObservableQueue.mutable<SubstituteLoggingEvent>()
        events.change.forEach(lifetime) {
            val ev = it.index
            val prefix = if (ev.level == Level.ERROR) "Error: " else ""
            logBuildData.add("${prefix}${ev.message}")
        }
        val logger = SubstituteLogger("ScriptModelBuilderLogger", events, false)

        val dslFile = DslFileFinder.find(project)

        if (dslFile == null)
        {
            logger.info("Can't find `circlet.kts`")
            return createEmptyScriptViewModel(lifetime)
        }

        try
        {
            val automationSettingsComponent = application.getComponent<CircletAutomationSettingsComponent>()
            val path = normalizePath(automationSettingsComponent.state.kotlincFolderPath)

            val kotlinCompilerPath = KotlinCompilerFinder(logger)
                .find(if (path.endsWith('/')) path else "$path/")

            val url = find(ScriptModelBuilder::class, "pipelines-config-dsl-scriptdefinition")

            val targetJar = createTempDir().absolutePath + "/compiledJar.jar"
            DslJarCompiler(logger).compile(DslSourceFileDelegatingFileProvider(dslFile.path), targetJar, kotlinCompilerPath, url.file)

            val config = DslScriptExecutor().evaluateModel(targetJar, "", "", "")
            config.applyIds()

            return ScriptViewModel(lifetime, config)
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
