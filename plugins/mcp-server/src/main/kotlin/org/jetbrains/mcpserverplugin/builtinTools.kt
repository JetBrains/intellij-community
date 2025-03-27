package org.jetbrains.mcpserverplugin

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

class GetCurrentFileTextTool : AbstractMcpTool<NoArgs>() {
    override val name: String = "get_open_in_editor_file_text"
    override val description: String = """
        Retrieves the complete text content of the currently active file in the JetBrains IDE editor.
        Use this tool to access and analyze the file's contents for tasks such as code review, content inspection, or text processing.
        Returns empty string if no file is currently open.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val text = runReadAction<String?> {
            getInstance(project).selectedTextEditor?.document?.text
        }
        return Response(text ?: "")
    }
}

class GetCurrentFilePathTool : AbstractMcpTool<NoArgs>() {
    override val name: String = "get_open_in_editor_file_path"
    override val description: String = """
        Retrieves the absolute path of the currently active file in the JetBrains IDE editor.
        Use this tool to get the file location for tasks requiring file path information.
        Returns an empty string if no file is currently open.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val path = runReadAction<String?> {
            getInstance(project).selectedTextEditor?.virtualFile?.path
        }
        return Response(path ?: "")
    }
}

class GetAllOpenFileTextsTool : AbstractMcpTool<NoArgs>() {
    override val name: String = "get_all_open_file_texts"
    override val description: String = """
        Returns text of all currently open files in the JetBrains IDE editor.
        Returns an empty list if no files are open.
        
        Use this tool to explore current open editors.
        Returns a JSON array of objects containing file information:
            - path: Path relative to project root
            - text: File text
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()

        val fileEditorManager = FileEditorManager.getInstance(project)
        val openFiles = fileEditorManager.openFiles
        val filePaths = openFiles.mapNotNull {
            """{"path": "${
                it.toNioPath().relativizeByProjectDir(projectDir)
            }", "text": "${it.readText()}", """
        }
        return Response(filePaths.joinToString(",\n", prefix = "[", postfix = "]"))
    }
}

class GetAllOpenFilePathsTool : AbstractMcpTool<NoArgs>() {
    override val name: String = "get_all_open_file_paths"
    override val description: String = """
        Lists full path relative paths to project root of all currently open files in the JetBrains IDE editor.
        Returns a list of file paths that are currently open in editor tabs.
        Returns an empty list if no files are open.
        
        Use this tool to explore current open editors.
        Returns a list of file paths separated by newline symbol.
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()

        val fileEditorManager = FileEditorManager.getInstance(project)
        val openFiles = fileEditorManager.openFiles
        val filePaths = openFiles.mapNotNull { it.toNioPath().relativizeByProjectDir(projectDir) }
        return Response(filePaths.joinToString("\n"))
    }
}

@Serializable
data class OpenFileInEditorArgs(val filePath: String)

class OpenFileInEditorTool : AbstractMcpTool<OpenFileInEditorArgs>() {
    override val name: String = "open_file_in_editor"
    override val description: String = """
        Opens the specified file in the JetBrains IDE editor.
        Requires a filePath parameter containing the path to the file to open.
        Requires two parameters:
            - filePath: The path of file to open can be absolute or relative to the project root.
            - text: The content to write into the new file
        Returns one of two possible responses:
            - "file is opened" if the file was successfully created and populated
            - "file doesn't exist or can't be opened" otherwise
    """.trimIndent()

    override fun handle(project: Project, args: OpenFileInEditorArgs): Response {
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return Response(error = "can't find project dir")

        val file = LocalFileSystem.getInstance().findFileByPath(args.filePath)
            ?: LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectDir.resolveRel(args.filePath))

        return if (file != null && file.exists()) {
            invokeLater {
                FileEditorManager.getInstance(project).openFile(file, true)
            }
            Response("file is opened")
        } else {
            Response("file doesn't exist or can't be opened")
        }
    }
}

class GetSelectedTextTool : AbstractMcpTool<NoArgs>() {
    override val name: String = "get_selected_in_editor_text"
    override val description: String = """
        Retrieves the currently selected text from the active editor in JetBrains IDE.
        Use this tool when you need to access and analyze text that has been highlighted/selected by the user.
        Returns an empty string if no text is selected or no editor is open.
    """

    override fun handle(project: Project, args: NoArgs): Response {
        val text = runReadAction<String?> {
            getInstance(project).selectedTextEditor?.selectionModel?.selectedText
        }
        return Response(text ?: "")
    }
}

@Serializable
data class ReplaceSelectedTextArgs(val text: String)

class ReplaceSelectedTextTool : AbstractMcpTool<ReplaceSelectedTextArgs>() {
    override val name: String = "replace_selected_text"
    override val description: String = """
        Replaces the currently selected text in the active editor with specified new text.
        Use this tool to modify code or content by replacing the user's text selection.
        Requires a text parameter containing the replacement content.
        Returns one of three possible responses:
            - "ok" if the text was successfully replaced
            - "no text selected" if no text is selected or no editor is open
            - "unknown error" if the operation fails
    """.trimIndent()

    override fun handle(project: Project, args: ReplaceSelectedTextArgs): Response {
        var response: Response? = null

        application.invokeAndWait {
            runWriteCommandAction(project, "Replace Selected Text", null, {
                val editor = getInstance(project).selectedTextEditor
                val document = editor?.document
                val selectionModel = editor?.selectionModel
                if (document != null && selectionModel != null && selectionModel.hasSelection()) {
                    document.replaceString(selectionModel.selectionStart, selectionModel.selectionEnd, args.text)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    response = Response("ok")
                } else {
                    response = Response(error = "no text selected")
                }
            })
        }

        return response ?: Response(error = "unknown error")
    }
}

@Serializable
data class ReplaceCurrentFileTextArgs(val text: String)

class ReplaceCurrentFileTextTool : AbstractMcpTool<ReplaceCurrentFileTextArgs>() {
    override val name: String = "replace_current_file_text"
    override val description: String = """
        Replaces the entire content of the currently active file in the JetBrains IDE with specified new text.
        Use this tool when you need to completely overwrite the current file's content.
        Requires a text parameter containing the new content.
        Returns one of three possible responses:
        - "ok" if the file content was successfully replaced
        - "no file open" if no editor is active
        - "unknown error" if the operation fails
    """

    override fun handle(project: Project, args: ReplaceCurrentFileTextArgs): Response {
        var response: Response? = null
        application.invokeAndWait {
            runWriteCommandAction(project, "Replace File Text", null, {
                val editor = getInstance(project).selectedTextEditor
                val document = editor?.document
                if (document != null) {
                    document.setText(args.text)
                    response = Response("ok")
                } else {
                    response = Response(error = "no file open")
                }
            })
        }
        return response ?: Response(error = "unknown error")
    }
}

@Serializable
data class CreateNewFileWithTextArgs(val pathInProject: String, val text: String)

class CreateNewFileWithTextTool : AbstractMcpTool<CreateNewFileWithTextArgs>() {
    override val name: String = "create_new_file_with_text"
    override val description: String = """
        Creates a new file at the specified path within the project directory and populates it with the provided text.
        Use this tool to generate new files in your project structure.
        Requires two parameters:
            - pathInProject: The relative path where the file should be created
            - text: The content to write into the new file
        Returns one of two possible responses:
            - "ok" if the file was successfully created and populated
            - "can't find project dir" if the project directory cannot be determined
        Note: Creates any necessary parent directories automatically
    """

    override fun handle(project: Project, args: CreateNewFileWithTextArgs): Response {
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return Response(error = "can't find project dir")

        val path = projectDir.resolveRel(args.pathInProject)
        if (!path.exists()) {
            path.createParentDirectories().createFile()
        }
        val text = args.text
        path.writeText(text.unescape())
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)

        return Response("ok")
    }
}

private fun String.unescape(): String = removePrefix("<![CDATA[").removeSuffix("]]>")

@Serializable
data class Query(val nameSubstring: String)

class FindFilesByNameSubstring : AbstractMcpTool<Query>() {
    override val name: String = "find_files_by_name_substring"
    override val description: String = """
        Searches for all files in the project whose names contain the specified substring (case-insensitive).
        Use this tool to locate files when you know part of the filename.
        Requires a nameSubstring parameter for the search term.
        Returns a JSON array of objects containing file information:
        - path: Path relative to project root
        - name: File name
        Returns an empty array ([]) if no matching files are found.
        Note: Only searches through files within the project directory, excluding libraries and external dependencies.
    """

    override fun handle(project: Project, args: Query): Response {
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return Response(error = "project dir not found")

        val searchSubstring = args.nameSubstring.toLowerCase()
        return runReadAction {
            Response(
                FilenameIndex.getAllFilenames(project)
                    .filter { it.toLowerCase().contains(searchSubstring) }
                    .flatMap {
                        FilenameIndex.getVirtualFilesByName(it, GlobalSearchScope.projectScope(project))
                    }
                    .filter { file ->
                        try {
                            projectDir.relativize(Path(file.path))
                            true
                        } catch (e: IllegalArgumentException) {
                            false
                        }
                    }
                    .map { file ->
                        val relativePath = projectDir.relativize(Path(file.path)).toString()
                        """{"path": "$relativePath", "name": "${file.name}"}"""
                    }
                    .joinToString(",\n", prefix = "[", postfix = "]")
            )
        }
    }
}


@Serializable
data class PathInProject(val pathInProject: String)

class GetFileTextByPathTool : AbstractMcpTool<PathInProject>() {
    override val name: String = "get_file_text_by_path"
    override val description: String = """
        Retrieves the text content of a file using its path relative to project root.
        Use this tool to read file contents when you have the file's project-relative path.
        Requires a pathInProject parameter specifying the file location from project root.
        Returns one of these responses:
        - The file's content if the file exists and belongs to the project
        - error "project dir not found" if project directory cannot be determined
        - error "file not found" if the file doesn't exist or is outside project scope
        Note: Automatically refreshes the file system before reading
    """

    override fun handle(project: Project, args: PathInProject): Response {
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return Response(error = "project dir not found")

        val text = runReadAction {
            val file = LocalFileSystem.getInstance()
                .refreshAndFindFileByNioFile(projectDir.resolveRel(args.pathInProject))
                ?: return@runReadAction Response(error = "file not found")

            if (GlobalSearchScope.allScope(project).contains(file)) {
                Response(file.readText())
            } else {
                Response(error = "file not found")
            }
        }
        return text
    }
}


@Serializable
data class ReplaceTextByPathToolArgs(val pathInProject: String, val text: String)

class ReplaceTextByPathTool : AbstractMcpTool<ReplaceTextByPathToolArgs>() {
    override val name: String = "replace_file_text_by_path"
    override val description: String = """
        Replaces the entire content of a specified file with new text, if the file is within the project.
        Use this tool to modify file contents using a path relative to the project root.
        Requires two parameters:
        - pathInProject: The path to the target file, relative to project root
        - text: The new content to write to the file
        Returns one of these responses:
        - "ok" if the file was successfully updated
        - error "project dir not found" if project directory cannot be determined
        - error "file not found" if the file doesn't exist
        - error "could not get document" if the file content cannot be accessed
        Note: Automatically saves the file after modification
    """

    override fun handle(project: Project, args: ReplaceTextByPathToolArgs): Response {
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return Response(error = "project dir not found")

        var document: Document? = null

        val readResult = runReadAction {
            var file: VirtualFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByNioFile(projectDir.resolveRel(args.pathInProject))
                ?: return@runReadAction "file not found"

            if (!GlobalSearchScope.allScope(project).contains(file)) {
                return@runReadAction "file not found"
            }

            document = FileDocumentManager.getInstance().getDocument(file)
            if (document == null) {
                return@runReadAction "could not get document"
            }

            return@runReadAction "ok"
        }

        if (readResult != "ok") {
            return Response(error = readResult)
        }

        WriteCommandAction.runWriteCommandAction(project) {
            document!!.setText(args.text)
            FileDocumentManager.getInstance().saveDocument(document!!)
        }

        return Response("ok")
    }
}


@Serializable
data class ListFilesInFolderArgs(val pathInProject: String)

class ListFilesInFolderTool : AbstractMcpTool<ListFilesInFolderArgs>() {
    override val name: String = "list_files_in_folder"
    override val description: String = """
        Lists all files and directories in the specified project folder.
        Use this tool to explore project structure and get contents of any directory.
        Requires a pathInProject parameter (use "/" for project root).
        Returns a JSON-formatted list of entries, where each entry contains:
        - name: The name of the file or directory
        - type: Either "file" or "directory"
        - path: Full path relative to project root
        Returns error if the specified path doesn't exist or is outside project scope.
    """.trimIndent()

    override fun handle(project: Project, args: ListFilesInFolderArgs): Response {
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return Response(error = "can't find project dir")

        return runReadAction {
            try {
                val targetDir = projectDir.resolveRel(args.pathInProject)

                if (!targetDir.exists()) {
                    return@runReadAction Response(error = "directory not found")
                }

                val entries = targetDir.listDirectoryEntries().map { entry ->
                    val type = if (entry.isDirectory()) "directory" else "file"
                    val relativePath = projectDir.relativize(entry).toString()
                    """{"name": "${entry.name}", "type": "$type", "path": "$relativePath"}"""
                }

                Response(entries.joinToString(",\n", prefix = "[", postfix = "]"))
            } catch (e: Exception) {
                Response(error = "Error listing directory: ${e.message}")
            }
        }
    }
}

@Serializable
data class SearchInFilesArgs(val searchText: String)

class SearchInFilesContentTool : AbstractMcpTool<SearchInFilesArgs>() {
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

class GetRunConfigurationsTool : AbstractMcpTool<NoArgs>() {
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

class GetProjectModulesTool : AbstractMcpTool<NoArgs>() {
    override val name: String = "get_project_modules"
    override val description: String =
        "Get list of all modules in the project with their dependencies. Returns JSON list of module names."

    override fun handle(project: Project, args: NoArgs): Response {
        val moduleManager = com.intellij.openapi.module.ModuleManager.getInstance(project)
        val modules = moduleManager.modules.map { it.name }
        return Response(modules.joinToString(",\n", prefix = "[", postfix = "]"))
    }
}

class GetProjectDependenciesTool : AbstractMcpTool<NoArgs>() {
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

class ListAvailableActionsTool : AbstractMcpTool<NoArgs>() {
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

class ExecuteActionByIdTool : AbstractMcpTool<ExecuteActionArgs>() {
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

class GetProgressIndicatorsTool : AbstractMcpTool<NoArgs>() {
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

class WaitTool : AbstractMcpTool<WaitArgs>() {
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