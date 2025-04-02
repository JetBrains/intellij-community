package org.jetbrains.mcpserverplugin.general

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.find.FindManager
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.usageView.UsageInfo
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageViewPresentation
import com.intellij.util.Processor
import kotlinx.serialization.Serializable
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.pathString

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

class RunConfigurationTool : AbstractMcpTool<RunConfigArgs>() {
    override val name: String = "run_configuration"
    override val description: String =
        "Run a specific run configuration in the current project and wait up to 120 seconds for it to finish. " +
                "Use this tool to run a run configuration that you have found from the \"get_run_configurations\" tool. " +
                "Returns the output (stdout/stderr) of the execution, prefixed with 'ok\\n' on success (exit code 0). " +
                "Returns '<error message>' if the configuration is not found, times out, fails to start, or finishes with a non-zero exit code."

    // Timeout in seconds
    private val executionTimeoutSeconds = 120L

    override fun handle(project: Project, args: RunConfigArgs): Response {
        val runManager = RunManager.getInstance(project)
        val settings = runManager.allSettings.find { it.name == args.configName }
            ?: return Response(error = "Run configuration with name '${args.configName}' not found.")

        val executor =
            DefaultRunExecutor.getRunExecutorInstance() ?: return Response(error = "Default 'Run' executor not found.")

        val future = CompletableFuture<Pair<Int, String>>() // Pair<ExitCode, Output>
        val outputBuilder = StringBuilder()

        ApplicationManager.getApplication().invokeLater {
            try {
                val runner: ProgramRunner<*>? = ProgramRunner.getRunner(executor.id, settings.configuration)
                if (runner == null) {
                    future.completeExceptionally(
                        ExecutionException("No suitable runner found for configuration '${settings.name}' and executor '${executor.id}'")
                    )
                    return@invokeLater
                }

                val environment = ExecutionEnvironmentBuilder.create(project, executor, settings.configuration).build()

                val callback = object : ProgramRunner.Callback {
                    override fun processStarted(descriptor: RunContentDescriptor?) {
                        if (descriptor == null) {
                            if (!future.isDone) {
                                future.completeExceptionally(
                                    ExecutionException("Run configuration doesn't support catching output")
                                )
                            }
                            return
                        }

                        val processHandler = descriptor.processHandler
                        if (processHandler == null) {
                            if (!future.isDone) {
                                future.completeExceptionally(
                                    IllegalStateException("Process handler is null even though RunContentDescriptor exists.")
                                )
                            }
                            return
                        }

                        processHandler.addProcessListener(object : ProcessAdapter() {
                            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                                synchronized(outputBuilder) {
                                    outputBuilder.append(event.text)
                                }
                            }

                            override fun processTerminated(event: ProcessEvent) {
                                val finalOutput = synchronized(outputBuilder) { outputBuilder.toString() }
                                future.complete(Pair(event.exitCode, finalOutput))
                            }

                            override fun processNotStarted() {
                                if (!future.isDone) {
                                    future.completeExceptionally(RuntimeException("Process explicitly reported as not started."))
                                }
                            }
                        })
                        processHandler.startNotify()
                    }
                }
                runner.execute(environment, callback)

            } catch (e: Throwable) {
                if (!future.isDone) {
                    future.completeExceptionally(
                        ExecutionException("Failed to prepare or start run configuration: ${e.message}", e)
                    )
                }
            }
        }

        try {
            val result = future.get(executionTimeoutSeconds, TimeUnit.SECONDS)
            val exitCode = result.first
            val output = result.second

            return if (exitCode == 0) {
                Response("ok\n$output")
            } else {
                Response(error = "Execution failed with exit code $exitCode.\nOutput:\n$output")
            }
        } catch (e: TimeoutException) {
            return Response(error = "Execution timed out after $executionTimeoutSeconds seconds.")
        } catch (e: ExecutionException) {
            val causeMessage = e.cause?.message ?: e.message
            return Response(error = "Failed to execute run configuration: $causeMessage")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return Response(error = "Execution was interrupted.")
        } catch (e: Throwable) {
            return Response(error = "An unexpected error occurred during execution wait: ${e.message}")
        }
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
        val presentationFactory = PresentationFactory()
        val visibleActions = invokeAndWaitIfNeeded {
            Utils.expandActionGroup(
                DefaultActionGroup(
                    actionIds.mapNotNull { actionManager.getAction(it) }
                ), presentationFactory, dataContext, "", ActionUiKind.NONE)
        }
        val availableActions = visibleActions.mapNotNull {
            val presentation = presentationFactory.getPresentation(it)
            val actionId = actionManager.getId(it)
            if (presentation.isEnabledAndVisible && !presentation.text.isNullOrBlank()) {
                """{"id": "$actionId", "text": "${presentation.text.replace("\"", "\\\"")}"}"""
            } else null
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