package com.intellij.mcpserver.util

import com.intellij.mcpserver.mcpFail
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path

/**
 * Returns the project's base directory as a [Path].
 *
 * If the project directory cannot be determined, an McpExpectedException is thrown.
 */
val Project.projectDirectory: Path
  get() = guessProjectDir()?.toNioPathOrNull() ?: mcpFail("The project directory cannot be determined.")

/**
 * Resolves a relative path against the project's directory.
 *
 * When [throwWhenOutside] is true the method throws an McpExpectedException if the path is outside the project directory.
 */
fun Project.resolveInProject(pathInProject: String, throwWhenOutside: Boolean = true): Path {
  val filePath = projectDirectory.resolve(pathInProject).normalize()
  if (throwWhenOutside && !filePath.startsWith(projectDirectory)) mcpFail("Specified path '$filePath' points to the location outside of the project directory")
  return filePath
}

fun Path.relativizeIfPossible(virtualFile: VirtualFile): String {
  val nioPath = virtualFile.toNioPathOrNull()
                ?: try {
                  Paths.get(virtualFile.path)
                }
                catch (e: Throwable) {
                  null
                }
  return if (nioPath != null) relativize(nioPath).toString() else virtualFile.path
}

enum class RenderStyle {
  NameOnly,
  AbsolutePath,
}

suspend fun renderDirectoryTree(
  dir: File,
  result: StringBuilder,
  errorsBag: MutableList<String>,
  indent: String = "",
  isLast: Boolean = true,
  maxDepth: Int = 10,
  renderStyle: RenderStyle = RenderStyle.AbsolutePath,
) {
  if (maxDepth <= 0) return
  currentCoroutineContext().ensureActive()
  try {
    val prefix = when {
      indent.isEmpty() -> ""
      isLast -> "└── "
      else -> "├── "
    }
    result
      .append(indent)
      .append(prefix)
      .append(if (renderStyle == RenderStyle.AbsolutePath) dir.absolutePath else dir.name)
      .append(if (dir.isDirectory) "/" else "")
      .append("\n")

    if (dir.isDirectory) {
      val children = dir.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()
      for ((index, file) in children.withIndex()) {
        val isLastChild = index == children.lastIndex
        val newIndent = indent + if (isLast) "    " else "│   "
        renderDirectoryTree(file, result, errorsBag, newIndent, isLastChild, maxDepth - 1, renderStyle = RenderStyle.NameOnly)
      }
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    errorsBag.add("Failed to read ${dir.absolutePath}: ${e.message ?: "unknown error"}")
  }
}