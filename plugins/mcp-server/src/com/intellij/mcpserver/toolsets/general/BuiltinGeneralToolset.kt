@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

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
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.usageView.UsageInfo
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageViewPresentation
import com.intellij.util.Processor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.coroutineContext
import kotlin.io.path.Path

class BuiltinGeneralToolset : McpToolset {
    @McpTool
    @McpDescription("""
        Searches for a text substring within all files in the project using IntelliJ's search engine. It skips internal files like *.iml, *.ipr and files in .idea directory.
        Use this tool to find files containing specific text content.
        Requires a searchText parameter specifying the text to find.
        Returns a JSON array of objects containing file information:
        - path: Path relative to project root
        Returns an empty array ([]) if no matches are found.
        Note: Only searches through text files within the project directory.
    """)
    suspend fun search_in_files_content(
        @McpDescription("Text substring to search for")
        searchText: String
    ): String {
        val project = coroutineContext.project
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return "Project directory not found"

        if (searchText.isBlank()) {
            return "contentSubstring parameter is required and cannot be blank"
        }

        val findModel = FindManager.getInstance(project).findInProjectModel.clone()
        findModel.stringToFind = searchText
        findModel.isCaseSensitive = false
        findModel.isWholeWordsOnly = false
        findModel.isRegularExpressions = false
        findModel.isProjectScope = true
        findModel.isSearchInProjectFiles = false

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

        return results.joinToString(",\n", prefix = "[", postfix = "]")
    }

    @McpTool
    @McpDescription("""
        Returns a list of run configurations for the current project.
        Use this tool to query the list of available run configurations in current project.
        Then you shall to call "run_configuration" tool if you find anything relevant.
        Returns JSON list of run configuration names. Empty list if no run configurations found.
    """)
    suspend fun get_run_configurations(): String {
        val project = coroutineContext.project
        val runManager = RunManager.getInstance(project)

        val configurations = runManager.allSettings.map { it.name }.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ","
        ) { "\"$it\"" }

        return configurations
    }

    @McpTool
    @McpDescription("""
        Run a specific run configuration in the current project and wait up to 120 seconds for it to finish.
        Use this tool to run a run configuration that you have found from the "get_run_configurations" tool.
        Returns the output (stdout/stderr) of the execution, prefixed with 'ok\n' on success (exit code 0).
        Returns '<error message>' if the configuration is not found, times out, fails to start, or finishes with a non-zero exit code.
    """)
    suspend fun run_configuration(
        @McpDescription("Name of the run configuration to execute")
        configName: String
    ): String {
        val project = coroutineContext.project
        val executionTimeoutSeconds = 120L
        val runManager = RunManager.getInstance(project)
        val settings = runManager.allSettings.find { it.name == configName }
            ?: return "Run configuration with name '$configName' not found."

        val executor =
            DefaultRunExecutor.getRunExecutorInstance() ?: return "Default 'Run' executor not found."

        val future = CompletableFuture<Pair<Int, String>>() // Pair<ExitCode, Output>
        val outputBuilder = StringBuilder()

        ApplicationManager.getApplication().invokeLater {
            try {
                val runner: ProgramRunner<*>? = ProgramRunner.getRunner(executor.id, settings.configuration)
                if (runner == null) {
                    future.completeExceptionally(
                        ExecutionException(McpServerBundle.message("dialog.message.no.suitable.runner.found.for.configuration.executor", settings.name, executor.id))
                    )
                    return@invokeLater
                }

                val environment = ExecutionEnvironmentBuilder.create(project, executor, settings.configuration).build()

                val callback = object : ProgramRunner.Callback {
                    override fun processStarted(descriptor: RunContentDescriptor?) {
                        if (descriptor == null) {
                            if (!future.isDone) {
                                future.completeExceptionally(
                                    ExecutionException(McpServerBundle.message("dialog.message.run.configuration.doesn.t.support.catching.output"))
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
                        ExecutionException(McpServerBundle.message("dialog.message.failed.to.prepare.or.start.run.configuration", e.message.orEmpty()), e)
                    )
                }
            }
        }

        try {
            val result = future.get(executionTimeoutSeconds, TimeUnit.SECONDS)
            val exitCode = result.first
            val output = result.second

            return if (exitCode == 0) {
                "ok\n$output"
            } else {
                "Execution failed with exit code $exitCode.\nOutput:\n$output"
            }
        } catch (e: TimeoutException) {
            return "Execution timed out after $executionTimeoutSeconds seconds."
        } catch (e: ExecutionException) {
            val causeMessage = e.cause?.message ?: e.message
            return "Failed to execute run configuration: $causeMessage"
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return "Execution was interrupted."
        } catch (e: Throwable) {
            return "An unexpected error occurred during execution wait: ${e.message}"
        }
    }

    @McpTool
    @McpDescription("""
        Get list of all modules in the project with their dependencies. Returns JSON list of module names.
    """)
    suspend fun get_project_modules(): String {
        val project = coroutineContext.project
        val moduleManager = ModuleManager.getInstance(project)
        val modules = moduleManager.modules.map { it.name }
        return modules.joinToString(",\n", prefix = "[", postfix = "]")
    }

    @McpTool
    @McpDescription("""
        Get list of all dependencies defined in the project. Returns JSON list of dependency names.
    """)
    suspend fun get_project_dependencies(): String {
        val project = coroutineContext.project
        val moduleManager = ModuleManager.getInstance(project)
        val dependencies = moduleManager.modules.flatMap { module ->
            OrderEnumerator.orderEntries(module).librariesOnly().classes().roots.map { root ->
                """{"name": "${root.name}", "type": "library"}"""
            }
        }.toHashSet()

        return dependencies.joinToString(",\n", prefix = "[", postfix = "]")
    }

    @McpTool
    @McpDescription("""
        Lists all available actions in JetBrains IDE editor.
        Returns a JSON array of objects containing action information:
        - id: The action ID
        - text: The action presentation text
        Use this tool to discover available actions for execution with execute_action_by_id.
    """)
    suspend fun list_available_actions(): String {
        val project = coroutineContext.project
        val actionManager = ActionManager.getInstance() as ActionManagerEx
        val dataContext = invokeAndWaitIfNeeded {
            DataManager.getInstance().getDataContext()
        }

        val actionIds = actionManager.getActionIdList("")
        val presentationFactory = com.intellij.openapi.actionSystem.impl.PresentationFactory()
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
        return availableActions.joinToString(",\n", prefix = "[", postfix = "]")
    }

    @McpTool
    @McpDescription("""
        Executes an action by its ID in JetBrains IDE editor.
        Requires an actionId parameter containing the ID of the action to execute.
        Returns one of two possible responses:
        - "ok" if the action was successfully executed
        - "action not found" if the action with the specified ID was not found
        Note: This tool doesn't wait for the action to complete.
    """)
    suspend fun execute_action_by_id(
        @McpDescription("ID of the action to execute")
        actionId: String
    ): String {
        val project = coroutineContext.project
        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction(actionId)

        if (action == null) {
            return "action not found"
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

        return "ok"
    }

    @McpTool
    @McpDescription("""
        Retrieves the status of all running progress indicators in JetBrains IDE editor.
        Returns a JSON array of objects containing progress information:
        - text: The progress text/description
        - fraction: The progress ratio (0.0 to 1.0)
        - indeterminate: Whether the progress is indeterminate
        Returns an empty array if no progress indicators are running.
    """)
    suspend fun get_progress_indicators(): String {
        val runningIndicators = CoreProgressManager.getCurrentIndicators()

        val progressInfos = runningIndicators.map { indicator ->
            val text = indicator.text ?: ""
            val fraction = if (indicator.isIndeterminate) -1.0 else indicator.fraction
            val indeterminate = indicator.isIndeterminate

            """{"text": "${text.replace("\"", "\\\"")}", "fraction": $fraction, "indeterminate": $indeterminate}"""
        }

        return progressInfos.joinToString(",\n", prefix = "[", postfix = "]")
    }

    @McpTool
    @McpDescription("""
        Waits for a specified number of milliseconds (default: 5000ms = 5 seconds).
        Optionally accepts a milliseconds parameter to specify the wait duration.
        Returns "ok" after the wait completes.
        Use this tool when you need to pause before executing the next command.
    """)
    suspend fun wait(
        @McpDescription("Number of milliseconds to wait (default: 5000)")
        milliseconds: Long = 5000
    ): String {
        val waitTime = if (milliseconds <= 0) 5000 else milliseconds

        try {
            TimeUnit.MILLISECONDS.sleep(waitTime)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return "Wait interrupted"
        }

        return "ok"
    }
}