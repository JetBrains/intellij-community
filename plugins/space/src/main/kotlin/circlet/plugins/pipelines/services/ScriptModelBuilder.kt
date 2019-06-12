package circlet.plugins.pipelines.services

import circlet.components.*
import circlet.pipelines.config.dsl.compile.*
import circlet.pipelines.config.dsl.compile.util.*
import circlet.pipelines.config.dsl.script.exec.common.*
import circlet.pipelines.config.utils.*
import circlet.plugins.pipelines.utils.*
import circlet.plugins.pipelines.viewmodel.*
import circlet.utils.*
import com.intellij.openapi.project.*
import klogging.*
import org.slf4j.event.*
import org.slf4j.helpers.*
import runtime.reactive.*
import java.io.*

class ScriptModelBuilder {
    companion object : KLogging()

    fun build(lifetime: Lifetime, project: Project, logBuildData: LogData): ScriptViewModel {

        val basePath = project.basePath
        if (basePath == null) {
            return createEmptyScriptViewModel(lifetime)
        }

        val expectedFile = File(basePath, "Circlet.kts")
        if (!expectedFile.exists())
        {
            return createEmptyScriptViewModel(lifetime)
        }

        try
        {
            val automationSettingsComponent = application.getComponent<CircletAutomationSettingsComponent>()
            val path = normalizePath(automationSettingsComponent.state.kotlincFolderPath)
            val events = ObservableQueue.mutable<SubstituteLoggingEvent>()
            events.change.forEach(lifetime) {
                logBuildData.add(it.index.message)
            }
            val logger = SubstituteLogger("ScriptModelBuilderLogger", events, false)
            val kotlinCompilerPath = KotlinCompilerFinder(logger)
                .find(if (path.endsWith('/')) path else "$path/")

            val url = find(ScriptModelBuilder::class, "pipelines-config-dsl-scriptdefinition")

            val targetJar = createTempDir().absolutePath + "/compiledJar.jar"
            val sourceCodeResolver = LocalSourceCodeResolver()
            DslJarCompiler(logger).compile(expectedFile.absolutePath, targetJar, sourceCodeResolver, kotlinCompilerPath, url.file)

            val config = DslScriptExecutor().evaluateModel(targetJar, "", "", "")

            return ScriptViewModel(lifetime, config)
        }
        catch (e: Exception)
        {
            logger.error(e)
            return createEmptyScriptViewModel(lifetime)
        }
    }
}
