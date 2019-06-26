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
import com.intellij.openapi.vfs.*
import klogging.*
import org.slf4j.event.*
import org.slf4j.helpers.*
import runtime.reactive.*
import java.io.*
import java.io.PrintWriter
import java.io.StringWriter



class ScriptModelBuilder {
    companion object : KLogging()

    fun build(lifetime: Lifetime, project: Project, logBuildData: LogData): ScriptViewModel {

        val events = ObservableQueue.mutable<SubstituteLoggingEvent>()
        events.change.forEach(lifetime) {
            val ev = it.index
            val prefix = if (ev.level == Level.ERROR) "Error: " else ""
            logBuildData.add("${prefix}${ev.message}")
        }
        val logger = SubstituteLogger("ScriptModelBuilderLogger", events, false)

        val basePath = project.basePath
        if (basePath == null) {
            logger.info("Can't build model for default project")
            return createEmptyScriptViewModel(lifetime)
        }

        val baseDirFile = LocalFileSystem.getInstance().findFileByPath(basePath)

        if (baseDirFile == null) {
            logger.info("Can't find file for base dir")
            return createEmptyScriptViewModel(lifetime)
        }

        val expectedFileName = "circlet.kts"
        val dslFile = baseDirFile.children.firstOrNull {
            expectedFileName.equals(it.name, true)
        }

        if (dslFile == null)
        {
            logger.info("Can't find `$expectedFileName`")
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
