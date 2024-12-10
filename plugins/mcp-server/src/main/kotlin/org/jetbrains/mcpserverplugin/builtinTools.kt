package org.jetbrains.mcpserverplugin

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager.getInstance
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.readText
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.io.createParentDirectories
import kotlinx.serialization.Serializable
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.writeText

// tools

class GetCurrentFileTextTool : AbstractMcpTool<NoArgs>() {
    override val name: String = "get_open_in_editor_file_text"
    override val description: String = "Get the contents of the file which currently is opened in selected editor in JetBrains IDE"

    override fun handle(project: Project, args: NoArgs): Response {
        val text = runReadAction<String?> {
            getInstance(project).selectedTextEditor?.document?.text
        }
        return Response(text)
    }
}

class GetCurrentFilePathTool : AbstractMcpTool<NoArgs>() {
    override val name: String = "get_open_in_editor_file_path"
    override val description: String = "Get the absolute path of the file which currently is opened in selected editor in JetBrains IDE"

    override fun handle(project: Project, args: NoArgs): Response {
        val path = runReadAction<String?> {
            getInstance(project).selectedTextEditor?.virtualFile?.path
        }
        return Response(path)
    }
}

class GetSelectedTextTool : AbstractMcpTool<NoArgs>() {
    override val name: String = "get_open_in_editor_text"
    override val description: String = "Get the currently selected text in open editor the JetBrains IDE"

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
    override val description: String = "Replace the currently selected text in the JetBrains IDE with new text"

    override fun handle(project: Project, args: ReplaceSelectedTextArgs): Response {
        runInEdt {
            runWriteCommandAction(project, "Replace Selected Text", null, {
                val editor = getInstance(project).selectedTextEditor
                val document = editor?.document
                val selectionModel = editor?.selectionModel
                if (document != null && selectionModel != null && selectionModel.hasSelection()) {
                    document.replaceString(selectionModel.selectionStart, selectionModel.selectionEnd, args.text)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                }
            })
        }
        return Response("ok")
    }
}

@Serializable
data class ReplaceCurrentFileTextArgs(val text: String)

class ReplaceCurrentFileTextTool : AbstractMcpTool<ReplaceCurrentFileTextArgs>() {
    override val name: String = "replace_current_file_text"
    override val description: String = "Replace the entire contents of the current file in JetBrains IDE with new text"

    override fun handle(project: Project, args: ReplaceCurrentFileTextArgs): Response {
        runInEdt {
            runWriteCommandAction(project, "Replace File Text", null, {
                val editor = getInstance(project).selectedTextEditor
                val document = editor?.document
                document?.setText(args.text)
            })
        }
        return Response("ok")
    }
}

@Serializable
data class CreateNewFileWithTextArgs(val pathInProject: String, val text: String)

class CreateNewFileWithTextTool : AbstractMcpTool<CreateNewFileWithTextArgs>() {
    override val name: String = "create_new_file_with_text"
    override val description: String = "Create a new file inside the project with specified text in JetBrains IDE"

    override fun handle(project: Project, args: CreateNewFileWithTextArgs): Response {
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return Response("can't find project dir")

        val path = Path(args.pathInProject)
        projectDir.resolve(path).createParentDirectories().createFile().writeText(args.text)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)

        return Response("ok")
    }
}

@Serializable
data class Query(val nameSubstring: String)

class FindFilesByNameSubstring: AbstractMcpTool<Query>() {
    override val name: String = "find_files_by_name_substring"
    override val description: String = "Find files inside the projct using name substring in JetBrains IDE"

    override fun handle(project: Project, args: Query): Response {
        val searchSubstring = args.nameSubstring.toLowerCase()
        return runReadAction {
            Response(FilenameIndex.getAllFilenames(project).filter {
                it.toLowerCase().contains(searchSubstring)
        }.flatMap {
                FilenameIndex.getVirtualFilesByName(it, GlobalSearchScope.allScope(project))
        }.map {
            it.path
            }.joinToString(",\n"))
        }
    }
}

data class Path(val absolutePath: String)
class GetFileTextByPathTool : AbstractMcpTool<Path>() {
    override val name: String = "get_file_text_by_path"
    override val description: String = "Get the contents of the file by its absolute path in JetBrains IDE if file belongs to the project"

    override fun handle(project: Project, args: Path): Response {
        val text = runReadAction {
            val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path(args.absolutePath)) ?: return@runReadAction null
            if (GlobalSearchScope.allScope(project).contains(file)) {
                file.readText()
            }
            else {
                null
            }
        }
        return Response(text)
    }
}