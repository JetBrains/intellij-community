package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectToml
import com.jetbrains.python.Result
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.visitFileTree

// Tools to walk FS and parse pyproject.toml

internal suspend fun walkFileSystem(root: Directory): FSWalkInfo {
  val files = ArrayList<Path>(10)
  val excludeDir = ArrayList<Directory>(10)
  withContext(Dispatchers.IO) {
    root.visitFileTree {
      onVisitFile { file, _ ->
        if (file.name == PY_PROJECT_TOML) {
          files.add(file)
        }
        return@onVisitFile FileVisitResult.CONTINUE
      }
      onPostVisitDirectory { directory, _ ->
        return@onPostVisitDirectory if (directory.name.startsWith(".")) {
          excludeDir.add(directory)
          FileVisitResult.SKIP_SUBTREE
        }
        else {
          FileVisitResult.CONTINUE
        }
      }
    }
  }
  // TODO: with a big number of files, use `chunk` to parse them concurrently
  val tomlFiles = files.map { file ->
    val toml = readFile(file) ?: return@map null
    file to toml
  }.filterNotNull().toMap()
  return FSWalkInfo(tomlFiles = tomlFiles, excludeDir.toSet())
}

internal data class FSWalkInfo(val tomlFiles: Map<Path, PyProjectToml>, val excludeDir: Set<Directory>)

private val logger = fileLogger()

private suspend fun readFile(file: Path): PyProjectToml? {
  val content = try {
    withContext(Dispatchers.IO) { file.readText() }
  }
  catch (e: IOException) {
    logger.warn("Can't read $file", e)
    return null
  }
  return when (val r = withContext(Dispatchers.Default) { PyProjectToml.parse(content) }) {
    is Result.Failure -> {
      logger.warn("Errors on $file: ${r.error.joinToString(", ")}")
      null
    }
    is Result.Success -> r.result
  }
}
