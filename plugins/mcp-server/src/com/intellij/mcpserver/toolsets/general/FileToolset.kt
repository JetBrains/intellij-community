@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.mcpserver.util.relativizeByProjectDir
import com.intellij.mcpserver.util.resolveRel
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.io.createParentDirectories
import java.nio.file.Path
import kotlin.coroutines.coroutineContext
import kotlin.io.path.*

class FileToolset : McpToolset {
    @McpTool
    @McpDescription("""
        Provides a hierarchical tree view of the project directory structure starting from the specified folder.
        Use this tool to efficiently explore complex project structures in a nested format.
        Requires a pathInProject parameter (use "/" for project root).
        Optionally accepts a maxDepth parameter (default: 5) to limit recursion depth.
        Returns a JSON-formatted tree structure, where each entry contains:
        - name: The name of the file or directory
        - type: Either "file" or "directory"
        - path: Full path relative to project root
        - children: Array of child entries (for directories only)
        Returns error if the specified path doesn't exist or is outside project scope.
    """)
    suspend fun list_directory_tree_in_folder(
        @McpDescription("Path in project (use \"/\" for project root)")
        pathInProject: String,
        @McpDescription("Maximum recursion depth (default: 5)")
        maxDepth: Int = 5
    ): String {
        val project = coroutineContext.project
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return "can't find project dir"

        return runReadAction {
            try {
                val targetDir = projectDir.resolveRel(pathInProject)

                if (!targetDir.exists()) {
                    return@runReadAction "directory not found"
                }

                val entryTree = buildDirectoryTree(projectDir, targetDir, maxDepth)
                entryTreeToJson(entryTree)
            } catch (e: Exception) {
                "Error creating directory tree: ${e.message}"
            }
        }
    }

    @McpTool
    @McpDescription("""
        Lists all files and directories in the specified project folder.
        Use this tool to explore project structure and get contents of any directory.
        Requires a pathInProject parameter (use "/" for project root).
        Returns a JSON-formatted list of entries, where each entry contains:
        - name: The name of the file or directory
        - type: Either "file" or "directory"
        - path: Full path relative to project root
        Returns error if the specified path doesn't exist or is outside project scope.
    """)
    suspend fun list_files_in_folder(
        @McpDescription("Path in project (use \"/\" for project root)")
        pathInProject: String
    ): String {
        val project = coroutineContext.project
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return "can't find project dir"

        return runReadAction {
            try {
                val targetDir = projectDir.resolveRel(pathInProject)

                if (!targetDir.exists()) {
                    return@runReadAction "directory not found"
                }

                val entries = targetDir.listDirectoryEntries().map { entry ->
                    val type = if (entry.isDirectory()) "directory" else "file"
                    val relativePath = projectDir.relativize(entry).toString()
                    """{"name": "${entry.name}", "type": "$type", "path": "$relativePath"}"""
                }

                entries.joinToString(",\n", prefix = "[", postfix = "]")
            } catch (e: Exception) {
                "Error listing directory: ${e.message}"
            }
        }
    }

    @McpTool
    @McpDescription("""
        Searches for all files in the project whose names contain the specified substring (case-insensitive).
        Use this tool to locate files when you know part of the filename.
        Requires a nameSubstring parameter for the search term.
        Returns a JSON array of objects containing file information:
        - path: Path relative to project root
        - name: File name
        Returns an empty array ([]) if no matching files are found.
        Note: Only searches through files within the project directory, excluding libraries and external dependencies.
    """)
    suspend fun find_files_by_name_substring(
        @McpDescription("Substring to search for in file names")
        nameSubstring: String
    ): String {
        val project = coroutineContext.project
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return "project dir not found"

        val searchSubstring = nameSubstring.lowercase()
        return runReadAction {
            FilenameIndex.getAllFilenames(project)
                .filter { it.lowercase().contains(searchSubstring) }
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
        }
    }

    @McpTool
    @McpDescription("""
        Creates a new file at the specified path within the project directory and populates it with the provided text.
        Use this tool to generate new files in your project structure.
        Requires two parameters:
            - pathInProject: The relative path where the file should be created
            - text: The content to write into the new file
        Returns one of two possible responses:
            - "ok" if the file was successfully created and populated
            - "can't find project dir" if the project directory cannot be determined
        Note: Creates any necessary parent directories automatically
    """)
    suspend fun create_new_file_with_text(
        @McpDescription("Path in project where the file should be created")
        pathInProject: String,
        @McpDescription("Content to write into the new file")
        text: String
    ): String {
        val project = coroutineContext.project
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return "can't find project dir"

        val path = projectDir.resolveRel(pathInProject)
        if (!path.exists()) {
            path.createParentDirectories().createFile()
        }
        val textContent = text.removePrefix("<![CDATA[").removeSuffix("]]>")
        path.writeText(textContent)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)

        return "ok"
    }

    @McpTool
    @McpDescription("""
        Opens the specified file in the JetBrains IDE editor.
        Requires a filePath parameter containing the path to the file to open.
        The file path can be absolute or relative to the project root.
        Returns one of two possible responses:
            - "file is opened" if the file was successfully opened
            - "file doesn't exist or can't be opened" otherwise
    """)
    suspend fun open_file_in_editor(
        @McpDescription("Path of file to open (absolute or relative to project root)")
        filePath: String
    ): String {
        val project = coroutineContext.project
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return "can't find project dir"

        val file = LocalFileSystem.getInstance().findFileByPath(filePath)
            ?: LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectDir.resolveRel(filePath))

        return if (file != null && file.exists()) {
            invokeLater {
                FileEditorManager.getInstance(project).openFile(file, true)
            }
            "file is opened"
        } else {
            "file doesn't exist or can't be opened"
        }
    }

    @McpTool
    @McpDescription("""
        Lists full path relative paths to project root of all currently open files in the JetBrains IDE editor.
        Returns a list of file paths that are currently open in editor tabs.
        Returns an empty list if no files are open.
        
        Use this tool to explore current open editors.
        Returns a list of file paths separated by newline symbol.
    """)
    suspend fun get_all_open_file_paths(): String {
        val project = coroutineContext.project
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()

        val fileEditorManager = FileEditorManager.getInstance(project)
        val openFiles = fileEditorManager.openFiles
        val filePaths = openFiles.mapNotNull { it.toNioPath().relativizeByProjectDir(projectDir) }
        return filePaths.joinToString("\n")
    }

    @McpTool
    @McpDescription("""
        Retrieves the absolute path of the currently active file in the JetBrains IDE editor.
        Use this tool to get the file location for tasks requiring file path information.
        Returns an empty string if no file is currently open.
    """)
    suspend fun get_open_in_editor_file_path(): String {
        val project = coroutineContext.project
        val path = runReadAction<String?> {
            FileEditorManager.getInstance(project).selectedTextEditor?.virtualFile?.path
        }
        return path ?: ""
    }

    private data class Entry(
        val name: String,
        val type: String,
        val path: String,
        val children: MutableList<Entry> = mutableListOf()
    )

    private fun buildDirectoryTree(projectDir: Path, current: Path, maxDepth: Int, currentDepth: Int = 0): Entry {
        val relativePath = projectDir.relativize(current).toString()
        val type = if (current.isDirectory()) "directory" else "file"
        val entry = Entry(name = current.name, type = type, path = relativePath)
        if (current.isDirectory()) {
            if (currentDepth >= maxDepth) return entry
            current.listDirectoryEntries().forEach { childPath ->
                entry.children.add(buildDirectoryTree(projectDir, childPath, maxDepth, currentDepth + 1))
            }
        }
        return entry
    }

    private fun entryTreeToJson(entry: Entry): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"name\":\"${entry.name}\",")
        sb.append("\"type\":\"${entry.type}\",")
        sb.append("\"path\":\"${entry.path}\"")
        if (entry.type == "directory") {
            sb.append(",\"children\":[")
            entry.children.forEachIndexed { index, child ->
                if (index > 0) sb.append(",")
                sb.append(entryTreeToJson(child))
            }
            sb.append("]")
        }
        sb.append("}")
        return sb.toString()
    }
}