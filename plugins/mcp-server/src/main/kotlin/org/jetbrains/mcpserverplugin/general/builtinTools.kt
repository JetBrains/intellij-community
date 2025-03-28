package org.jetbrains.mcpserverplugin.general

import com.intellij.execution.ProgramRunnerUtil.executeConfiguration
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance
import com.intellij.find.FindManager
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManager.getInstance
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usageView.UsageInfo
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageViewPresentation
import com.intellij.util.Processor
import com.intellij.util.application
import com.intellij.util.io.createParentDirectories
import kotlinx.serialization.Serializable
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

fun Path.resolveRel(pathInProject: String): Path {
    return when (pathInProject) {
        "/" -> this
        else -> resolve(pathInProject.removePrefix("/"))
    }
}

fun Path.relativizeByProjectDir(projDir: Path?): String =
    projDir?.relativize(this)?.pathString ?: this.absolutePathString()

@Serializable
data class SearchInFilesArgs(val searchText: String)

class SearchInFilesContentTool : org.jetbrains.mcpserverplugin.AbstractMcpTool<SearchInFilesArgs>() {
    override val name: String = "search_in_files_content"
    override val description: String = """
        Searches for a text substring within all files in the project using IntelliJ's search engine.
        Use this tool to find files containing specific text content.
        Requires a searchText parameter specifying the text to find.
        Returns a JSON array of objects containing file information:
        - path: Path relative to project root
        Returns an empty array ([]) if no matches are found.
        Note: Only searches through text files within the project directory.
    """

    override fun handle(project: Project, args: SearchInFilesArgs): Response {
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return Response(error = "Project directory not found")

        val searchSubstring = args.searchText
        if (searchSubstring.isNullOrBlank()) {
            return Response(error = "contentSubstring parameter is required and cannot be blank")
        }

        val findModel = FindManager.getInstance(project).findInProjectModel.clone()
        findModel.stringToFind = searchSubstring
        findModel.isCaseSensitive = false
        findModel.isWholeWordsOnly = false
        findModel.isRegularExpressions = false
        findModel.setProjectScope(true)

        val results = mutableSetOf<String>()

        val processor = Processor<UsageInfo> { usageInfo ->
            val virtualFile = usageInfo.virtualFile ?: return@Processor true
            try {
                val relativePath = projectDir.relativize(Path(virtualFile.path)).toString()
                results.add("""{"path": "$relativePath", "name": "${virtualFile.name}"}""")
            } catch (e: IllegalArgumentException) {
            }
            true
        }
        FindInProjectUtil.findUsages(
            findModel,
            project,
            processor,
            FindUsagesProcessPresentation(UsageViewPresentation())
        )

        val jsonResult = results.joinToString(",\n", prefix = "[", postfix = "]")
        return Response(jsonResult)
    }
}

class GetRunConfigurationsTool : org.jetbrains.mcpserverplugin.AbstractMcpTool<NoArgs>() {
    override val name: String
        get() = "get_run_configurations"
    override val description: String
        get() = "Returns a list of run configurations for the current project. " +
                "Use this tool to query the list of available run configurations in current project." +
                "Then you shall to call \"run_configuration\" tool if you find anything relevant." +
                "Returns JSON list of run configuration names. Empty list if no run configurations found."

    override fun handle(project: Project, args: NoArgs): Response {
        val runManager = RunManager.getInstance(project)

        val configurations = runManager.allSettings.map { it.name }.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ","
        ) { "\"$it\"" }

        return Response(configurations)
    }
}

@Serializable
data class RunConfigArgs(val configName: String)

class RunConfigurationTool : org.jetbrains.mcpserverplugin.AbstractMcpTool<RunConfigArgs>() {
    override val name: String = "run_configuration"
    override val description: String = "Run a specific run configuration in the current project. " +
            "Use this tool to run a run configuration that you have found from \"get_run_configurations\" tool." +
            "Returns one of two possible responses: " +
            " - \"ok\" if the run configuration was successfully executed " +
            " - \"error <error message>\" if the run configuration was not found or failed to execute"

    override fun handle(project: Project, args: RunConfigArgs): Response {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.allSettings.find { it.name == args.configName }
        val executor = getRunExecutorInstance()
        if (settings != null) {
            executeConfiguration(settings, executor)
        } else {
            println("Run configuration with name '${args.configName}' not found.")
        }
        return Response("ok")
    }
}

class GetProjectModulesTool : org.jetbrains.mcpserverplugin.AbstractMcpTool<NoArgs>() {
    override val name: String = "get_project_modules"
    override val description: String =
        "Get list of all modules in the project with their dependencies. Returns JSON list of module names."

    override fun handle(project: Project, args: NoArgs): Response {
        val moduleManager = com.intellij.openapi.module.ModuleManager.getInstance(project)
        val modules = moduleManager.modules.map { it.name }
        return Response(modules.joinToString(",\n", prefix = "[", postfix = "]"))
    }
}

class GetProjectDependenciesTool : org.jetbrains.mcpserverplugin.AbstractMcpTool<NoArgs>() {
    override val name: String = "get_project_dependencies"
    override val description: String =
        "Get list of all dependencies defined in the project. Returns JSON list of dependency names."

    override fun handle(project: Project, args: NoArgs): Response {
        val moduleManager = com.intellij.openapi.module.ModuleManager.getInstance(project)
        val dependencies = moduleManager.modules.flatMap { module ->
            OrderEnumerator.orderEntries(module).librariesOnly().classes().roots.map { root ->
                """{"name": "${root.name}", "type": "library"}"""
            }
        }.toHashSet()

        return Response(dependencies.joinToString(",\n", prefix = "[", postfix = "]"))
    }
}

class ListAvailableActionsTool : org.jetbrains.mcpserverplugin.AbstractMcpTool<NoArgs>() {
    override val name: String = "list_available_actions"
    override val description: String = """
        Lists all available actions in JetBrains IDE editor.
        Returns a JSON array of objects containing action information:
        - id: The action ID
        - text: The action presentation text
        Use this tool to discover available actions for execution with execute_action_by_id.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val actionManager = ActionManager.getInstance() as ActionManagerEx
        val dataContext = invokeAndWaitIfNeeded {
            DataManager.getInstance().getDataContext()
        }

        val actionIds = actionManager.getActionIdList("")
        val availableActions = actionIds.mapNotNull { actionId ->
            val action = actionManager.getAction(actionId) ?: return@mapNotNull null
            val presentation = action.templatePresentation.clone()

            val event = AnActionEvent.createFromAnAction(action, null, "", dataContext)

            invokeAndWaitIfNeeded {
                runCatching { action.update(event) }
            }

            if (event.presentation.isEnabledAndVisible && !presentation.text.isNullOrBlank()) {
                """{"id": "$actionId", "text": "${presentation.text.replace("\"", "\\\"")}"}"""
            } else {
                null
            }
        }

        return Response(availableActions.joinToString(",\n", prefix = "[", postfix = "]"))
    }
}

@Serializable
data class ExecuteActionArgs(val actionId: String)

class ExecuteActionByIdTool : org.jetbrains.mcpserverplugin.AbstractMcpTool<ExecuteActionArgs>() {
    override val name: String = "execute_action_by_id"
    override val description: String = """
        Executes an action by its ID in JetBrains IDE editor.
        Requires an actionId parameter containing the ID of the action to execute.
        Returns one of two possible responses:
        - "ok" if the action was successfully executed
        - "action not found" if the action with the specified ID was not found
        Note: This tool doesn't wait for the action to complete.
    """.trimIndent()

    override fun handle(project: Project, args: ExecuteActionArgs): Response {
        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction(args.actionId)

        if (action == null) {
            return Response(error = "action not found")
        }

        ApplicationManager.getApplication().invokeLater({
            val event = AnActionEvent.createFromAnAction(
                action,
                null,
                "",
                DataManager.getInstance().getDataContext()
            )
            action.actionPerformed(event)
        }, ModalityState.nonModal())

        return Response("ok")
    }
}

class GetProgressIndicatorsTool : org.jetbrains.mcpserverplugin.AbstractMcpTool<NoArgs>() {
    override val name: String = "get_progress_indicators"
    override val description: String = """
        Retrieves the status of all running progress indicators in JetBrains IDE editor.
        Returns a JSON array of objects containing progress information:
        - text: The progress text/description
        - fraction: The progress ratio (0.0 to 1.0)
        - indeterminate: Whether the progress is indeterminate
        Returns an empty array if no progress indicators are running.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val runningIndicators = CoreProgressManager.getCurrentIndicators()

        val progressInfos = runningIndicators.map { indicator ->
            val text = indicator.text ?: ""
            val fraction = if (indicator.isIndeterminate) -1.0 else indicator.fraction
            val indeterminate = indicator.isIndeterminate

            """{"text": "${text.replace("\"", "\\\"")}", "fraction": $fraction, "indeterminate": $indeterminate}"""
        }

        return Response(progressInfos.joinToString(",\n", prefix = "[", postfix = "]"))
    }
}

@Serializable
data class WaitArgs(val milliseconds: Long = 5000)

class WaitTool : org.jetbrains.mcpserverplugin.AbstractMcpTool<WaitArgs>() {
    override val name: String = "wait"
    override val description: String = """
        Waits for a specified number of milliseconds (default: 5000ms = 5 seconds).
        Optionally accepts a milliseconds parameter to specify the wait duration.
        Returns "ok" after the wait completes.
        Use this tool when you need to pause before executing the next command.
    """.trimIndent()

    override fun handle(project: Project, args: WaitArgs): Response {
        val waitTime = if (args.milliseconds <= 0) 5000 else args.milliseconds

        try {
            TimeUnit.MILLISECONDS.sleep(waitTime)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return Response(error = "Wait interrupted")
        }

        return Response("ok")
    }
}