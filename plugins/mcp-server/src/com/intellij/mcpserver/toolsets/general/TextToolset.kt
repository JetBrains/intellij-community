@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.mcpserver.util.relativizeByProjectDir
import com.intellij.mcpserver.util.resolveRel
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.application
import kotlin.coroutines.coroutineContext

class TextToolset : McpToolset {
    @McpTool
    @McpDescription("""
        Retrieves the complete text content of the currently active file in the JetBrains IDE editor.
        Use this tool to access and analyze the file's contents for tasks such as code review, content inspection, or text processing.
        Returns empty string if no file is currently open.
    """)
    suspend fun get_open_in_editor_file_text(): String {
        val project = coroutineContext.project
        val text = runReadAction<String?> {
            FileEditorManager.getInstance(project).selectedTextEditor?.document?.text
        }
        return text ?: ""
    }

    @McpTool
    @McpDescription("""
        Returns text of all currently open files in the JetBrains IDE editor.
        Returns an empty list if no files are open.
        
        Use this tool to explore current open editors.
        Returns a JSON array of objects containing file information:
            - path: Path relative to project root
            - text: File text
    """)
    suspend fun get_all_open_file_texts(): String {
        val project = coroutineContext.project
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()

        val fileEditorManager = FileEditorManager.getInstance(project)
        val openFiles = fileEditorManager.openFiles
        val filePaths = openFiles.mapNotNull {
            """{"path": "${
                it.toNioPath().relativizeByProjectDir(projectDir)
            }", "text": "${it.readText()}", """
        }
        return filePaths.joinToString(",\n", prefix = "[", postfix = "]")
    }

    @McpTool
    @McpDescription("""
        Retrieves the currently selected text from the active editor in JetBrains IDE.
        Use this tool when you need to access and analyze text that has been highlighted/selected by the user.
        Returns an empty string if no text is selected or no editor is open.
    """)
    suspend fun get_selected_in_editor_text(): String {
        val project = coroutineContext.project
        val text = runReadAction<String?> {
            FileEditorManager.getInstance(project).selectedTextEditor?.selectionModel?.selectedText
        }
        return text ?: ""
    }

    @McpTool
    @McpDescription("""
        Replaces the currently selected text in the active editor with specified new text.
        Use this tool to modify code or content by replacing the user's text selection.
        Requires a text parameter containing the replacement content.
        Returns one of three possible responses:
            - "ok" if the text was successfully replaced
            - "no text selected" if no text is selected or no editor is open
            - "unknown error" if the operation fails
    """)
    suspend fun replace_selected_text(
        @McpDescription("Replacement text content")
        text: String
    ): String {
        val project = coroutineContext.project
        var response: String? = null

        application.invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project, McpServerBundle.message("command.name.replace.selected.text"), null, {
                val editor = FileEditorManager.getInstance(project).selectedTextEditor
                val document = editor?.document
                val selectionModel = editor?.selectionModel
                if (document != null && selectionModel != null && selectionModel.hasSelection()) {
                    document.replaceString(selectionModel.selectionStart, selectionModel.selectionEnd, text)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    response = "ok"
                } else {
                    response = "no text selected"
                }
            })
        }

        return response ?: "unknown error"
    }

    @McpTool
    @McpDescription("""
        Replaces the entire content of the currently active file in the JetBrains IDE with specified new text.
        Use this tool when you need to completely overwrite the current file's content.
        Requires a text parameter containing the new content.
        Returns one of three possible responses:
        - "ok" if the file content was successfully replaced
        - "no file open" if no editor is active
        - "unknown error" if the operation fails
    """)
    suspend fun replace_current_file_text(
        @McpDescription("New content for the file")
        text: String
    ): String {
        val project = coroutineContext.project
        var response: String? = null
        application.invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project, McpServerBundle.message("command.name.replace.file.text"), null, {
                val editor = FileEditorManager.getInstance(project).selectedTextEditor
                val document = editor?.document
                if (document != null) {
                    document.setText(text)
                    response = "ok"
                } else {
                    response = "no file open"
                }
            })
        }
        return response ?: "unknown error"
    }

    @McpTool
    @McpDescription("""
        Retrieves the text content of a file using its path relative to project root.
        Use this tool to read file contents when you have the file's project-relative path.
        Requires a pathInProject parameter specifying the file location from project root.
        Returns one of these responses:
        - The file's content if the file exists and belongs to the project
        - error "project dir not found" if project directory cannot be determined
        - error "file not found" if the file doesn't exist or is outside project scope
        Note: Automatically refreshes the file system before reading
    """)
    suspend fun get_file_text_by_path(
        @McpDescription("Path to file relative to project root")
        pathInProject: String
    ): String {
        val project = coroutineContext.project
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return "project dir not found"

        val text = runReadAction {
            val file = LocalFileSystem.getInstance()
                .refreshAndFindFileByNioFile(projectDir.resolveRel(pathInProject))
                ?: return@runReadAction "file not found"

            if (GlobalSearchScope.allScope(project).contains(file)) {
                file.readText()
            } else {
                "file not found"
            }
        }
        return text
    }

    @McpTool
    @McpDescription("""
        Replaces specific text occurrences in a file with new text.
        Use this tool to make targeted changes without replacing the entire file content.
        Use this method if the file is large and the change is smaller than the old text.
        Prioritize this tool among other editing tools. It's more efficient and granular in the most of cases.
        Requires three parameters:
        - pathInProject: The path to the target file, relative to project root
        - oldText: The text to be replaced
        - newText: The replacement text
        Returns one of these responses:
        - "ok" when replacement happend
        - error "project dir not found" if project directory cannot be determined
        - error "file not found" if the file doesn't exist
        - error "could not get document" if the file content cannot be accessed
        - error "no occurrences found" if the old text was not found in the file
        Note: Automatically saves the file after modification
    """)
    suspend fun replace_specific_text(
        @McpDescription("Path to target file relative to project root")
        pathInProject: String,
        @McpDescription("Text to be replaced")
        oldText: String,
        @McpDescription("Replacement text")
        newText: String
    ): String {
        val project = coroutineContext.project
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return "project dir not found"

        var document: Document? = null
        
        val readResult = runReadAction {
            val file: VirtualFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByNioFile(projectDir.resolveRel(pathInProject))
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
            return readResult
        }

        val text = document!!.text
        if (!text.contains(oldText)) {
            return "no occurrences found"
        }

        val newTextContent = text.replace(oldText, newText, true)
        WriteCommandAction.runWriteCommandAction(project) {
            document!!.setText(newTextContent)
            FileDocumentManager.getInstance().saveDocument(document!!)
        }

        return "ok"
    }

    @McpTool
    @McpDescription("""
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
    """)
    suspend fun replace_file_text_by_path(
        @McpDescription("Path to target file relative to project root")
        pathInProject: String,
        @McpDescription("New content for the file")
        text: String
    ): String {
        val project = coroutineContext.project
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return "project dir not found"

        var document: Document? = null

        val readResult = runReadAction {
            var file: VirtualFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByNioFile(projectDir.resolveRel(pathInProject))
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
            return readResult
        }

        WriteCommandAction.runWriteCommandAction(project) {
            document!!.setText(text)
            FileDocumentManager.getInstance().saveDocument(document!!)
        }

        return "ok"
    }
}