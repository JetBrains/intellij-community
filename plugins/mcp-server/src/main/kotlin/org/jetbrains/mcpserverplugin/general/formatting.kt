package org.jetbrains.mcpserverplugin.general

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager.getInstance
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import java.util.concurrent.CountDownLatch


class ReformatCurrentFileTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name: String = "reformat_current_file"
    override val description: String = """
        Reformats the opened file in the JetBrains IDE editor.
        This tool doesn't require any parameters as it operates on the file currently open in the editor.
        
        Returns one of two possible responses:
            - "ok" if the file was successfully reformatted
            - "file doesn't exist or can't be opened" if there is no file currently selected in the editor
    """.trimIndent()

    override fun handle(project: Project, args: NoArgs): Response {
        val latch = CountDownLatch(1)

        val psiFile = runReadAction {
            return@runReadAction getInstance(project).selectedTextEditor?.document?.run {
                PsiDocumentManager.getInstance(project).getPsiFile(this)
            }
        }

        if (psiFile == null) {return Response(error = "file doesn't exist or can't be opened")}

        val codeProcessor: ReformatCodeProcessor = ReformatCodeProcessor(psiFile, false)
        codeProcessor.setPostRunnable(Runnable {
            latch.countDown()
        })
        ApplicationManager.getApplication().invokeLater(Runnable { codeProcessor.run() })
        latch.await()
        return Response("ok")
    }
}

class ReformatFileTool : AbstractMcpTool<PathInProject>(PathInProject.serializer()) {
    override val name: String = "reformat_file"
    override val description: String = """
        Reformats a specified file in the JetBrains IDE.
        Use this tool to apply code formatting rules to a file identified by its path.
        Requires a pathInProject parameter specifying the file location relative to the project root.
        
        Returns one of these responses:
        - "ok" if the file was successfully reformatted
        - error "project dir not found" if project directory cannot be determined
        - error "file doesn't exist or can't be opened" if the file doesn't exist or cannot be accessed
    """.trimIndent()

    override fun handle(project: Project, args: PathInProject): Response {
        val latch = CountDownLatch(1)

        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return Response(error = "project dir not found")

        val file = runReadAction {
            LocalFileSystem.getInstance()
                .refreshAndFindFileByNioFile(projectDir.resolveRel(args.pathInProject))
        }

        if (file == null) {return Response(error = "file doesn't exist or can't be opened")}

        val psiFile = runReadAction {
            return@runReadAction PsiManager.getInstance(project).findFile(file)
        }

        if (psiFile == null) {return Response(error = "file doesn't exist or can't be opened")}

        val codeProcessor: ReformatCodeProcessor = ReformatCodeProcessor(psiFile, false)
        codeProcessor.setPostRunnable(Runnable {
            latch.countDown()
        })
        ApplicationManager.getApplication().invokeLater(Runnable { codeProcessor.run() })
        latch.await()
        return Response("ok")
    }
}
