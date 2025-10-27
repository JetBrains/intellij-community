package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.ProjectStructureInfo
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.Result
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.FileVisitResult
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.toPath
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

internal suspend fun getProjectStructureDefault(
  entries: Map<ProjectName, PyProjectTomlProject>,
  rootIndex: Map<Directory, ProjectName>,
  //  dependenciesGetter: (PyProjectTomlProject) -> Set<Directory>,
): ProjectStructureInfo = withContext(Dispatchers.Default) {
  val deps = entries.asSequence().associate { (name, entry) ->
    val deps = getDependenciesFromToml(entry.pyProjectToml).mapNotNull { dir ->
      rootIndex[dir] ?: run {
        logger.warn("Can't find project for dir $dir")
        null
      }
    }.toSet()
    Pair(name, deps)
  }
  ProjectStructureInfo(dependencies = deps, membersToWorkspace = emptyMap()) // No workspace info (yet)
}

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

@RequiresBackgroundThread
private fun getDependenciesFromToml(projectToml: PyProjectToml): Set<Directory> {
  val depsFromFile = projectToml.project?.dependencies?.project ?: emptyList()
  val moduleDependencies = depsFromFile
    .mapNotNull { depSpec ->
      val match = PEP_621_PATH_DEPENDENCY.matchEntire(depSpec) ?: return@mapNotNull null
      val (_, depUri) = match.destructured
      return@mapNotNull parseDepUri(depUri)
    }
  return moduleDependencies.toSet()
}


// e.g. "lib @ file:///home/user/projects/main/lib"
private val PEP_621_PATH_DEPENDENCY = """([\w-]+) @ (file:.*)""".toRegex()

private fun parseDepUri(depUri: String): Path? =
  try {
    URI(depUri).toPath()
  }
  catch (e: InvalidPathException) {
    logger.info("Dep $depUri points to wrong path", e)
    null
  }
  catch (e: URISyntaxException) {
    logger.info("Dep $depUri can't be parsed", e)
    null
  }